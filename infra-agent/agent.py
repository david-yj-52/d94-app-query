"""d94-infra-agent — 호스트·컨테이너 메트릭을 60초마다 d94-query /api/v1/infra/ingest/{nodeKey} 로 push.

env:
  INFRA_URL      기본 https://api.tongsung.site/tsh/api/v1/infra
  INFRA_TOKEN    d94_infra_... WRITE 토큰 (ADMIN 발급)
  NODE_KEY       예: delap, ap01, jetson-01
  NODE_NAME / NODE_TYPE / NODE_LOCATION   처음 보낼 때 노드 자동 등록용(선택)
  INTERVAL_SEC   기본 60
  HOST_ROOT      디스크 측정 경로, 기본 /hostfs (compose 가 호스트 / 를 ro 로 마운트)
  TOP_N          기본 15

stdlib + psutil 만 쓴다. 도커는 /var/run/docker.sock(ro) 를 직접 HTTP 로 읽는다.
"""
import http.client
import json
import os
import platform
import re
import shutil
import socket
import subprocess
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor

import psutil

URL = os.environ.get("INFRA_URL", "https://api.tongsung.site/tsh/api/v1/infra").rstrip("/")
TOKEN = os.environ.get("INFRA_TOKEN", "")
NODE_KEY = os.environ.get("NODE_KEY") or socket.gethostname()
INTERVAL = int(os.environ.get("INTERVAL_SEC", "60"))
HOST_ROOT = os.environ.get("HOST_ROOT", "/hostfs")
TOP_N = int(os.environ.get("TOP_N", "15"))
SOCK = "/var/run/docker.sock"
MB = 1024 * 1024

# 비밀이 실릴 수 있는 인자 값 가리기: --password x, --token=x, PASSWORD=x, ...
_SECRET = re.compile(r"((?:pass(?:word|wd)?|secret|token|api[_-]?key|credential)[^\s=]*)([=\s]+)(\S+)", re.I)


def redact(cmd):
    return _SECRET.sub(lambda m: m.group(1) + m.group(2) + "***", cmd)[:500]


class _UnixConn(http.client.HTTPConnection):
    def __init__(self):
        super().__init__("localhost", timeout=10)

    def connect(self):
        self.sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.sock.settimeout(10)
        self.sock.connect(SOCK)


def docker_get(path):
    c = _UnixConn()
    try:
        c.request("GET", path)
        r = c.getresponse()
        return json.loads(r.read()) if r.status == 200 else None
    finally:
        c.close()


def container_stat(c):
    s = docker_get(f"/containers/{c['Id']}/stats?stream=false") or {}
    mem = s.get("memory_stats", {})
    usage = mem.get("usage", 0) - mem.get("stats", {}).get("inactive_file", 0)  # docker stats 와 같은 계산
    limit = mem.get("limit") or 0
    cpu, pre = s.get("cpu_stats", {}), s.get("precpu_stats", {})
    cpu_d = cpu.get("cpu_usage", {}).get("total_usage", 0) - pre.get("cpu_usage", {}).get("total_usage", 0)
    sys_d = cpu.get("system_cpu_usage", 0) - pre.get("system_cpu_usage", 0)
    ncpu = cpu.get("online_cpus") or 1
    return {
        "pid": None,
        "procName": (c.get("Names") or ["?"])[0].lstrip("/"),
        "procType": "CONTAINER",
        "memRssMb": round(max(usage, 0) / MB, 1),
        "memPct": round(usage / limit * 100, 2) if limit else None,
        "cpuPct": round(cpu_d / sys_d * ncpu * 100, 2) if sys_d > 0 and cpu_d >= 0 else None,
        "commandLine": c.get("Image"),
    }


def containers():
    if not os.path.exists(SOCK):
        return []
    try:
        cs = docker_get("/containers/json") or []
        with ThreadPoolExecutor(max_workers=8) as ex:  # stats 는 컨테이너당 ~1초 샘플링이라 병렬
            return list(ex.map(container_stat, cs))
    except Exception as e:  # noqa: BLE001 — 도커가 없어도 호스트 메트릭은 보낸다
        print("docker error:", e, flush=True)
        return []


def processes():
    procs = []
    for p in psutil.process_iter(["pid", "name", "memory_info", "memory_percent", "cmdline"]):
        try:
            mi = p.info["memory_info"]
            if mi is None:
                continue
            procs.append((mi.rss, p))
        except (psutil.NoSuchProcess, psutil.AccessDenied):
            continue
    procs.sort(key=lambda t: t[0], reverse=True)
    out = []
    for rss, p in procs[:TOP_N]:
        try:
            out.append({
                "pid": p.pid,
                "procName": p.info["name"] or str(p.pid),
                "procType": "PROCESS",
                "memRssMb": round(rss / MB, 1),
                "memPct": round(p.info["memory_percent"] or 0, 2),
                "cpuPct": round(p.cpu_percent(interval=None), 2),
                "commandLine": redact(" ".join(p.info["cmdline"] or [])),
            })
        except (psutil.NoSuchProcess, psutil.AccessDenied):
            continue
    return out


def gpu():
    """(model, total MB, util %, used MB). nvidia-smi → Jetson tegrastats 순. 없으면 전부 None"""
    if shutil.which("nvidia-smi"):
        try:
            line = subprocess.check_output(
                ["nvidia-smi", "--query-gpu=name,memory.total,utilization.gpu,memory.used",
                 "--format=csv,noheader,nounits"], timeout=10, text=True).splitlines()[0]
            name, total, util, used = [x.strip() for x in line.split(",")]
            return name, int(float(total)), float(util), int(float(used))
        except Exception:  # noqa: BLE001
            pass
    if shutil.which("tegrastats"):
        try:
            p = subprocess.Popen(["tegrastats", "--interval", "1000"], stdout=subprocess.PIPE, text=True)
            line = p.stdout.readline()
            p.kill()
            m = re.search(r"GR3D_FREQ (\d+)%", line)
            # Jetson 은 통합 메모리 — GPU 메모리 = 시스템 RAM 이라 used/total 은 호스트 값으로 둔다
            return "Jetson (integrated)", None, float(m.group(1)) if m else None, None
        except Exception:  # noqa: BLE001
            pass
    return None, None, None, None


def os_info():
    try:
        with open(os.path.join(HOST_ROOT, "etc/os-release")) as f:
            for line in f:
                if line.startswith("PRETTY_NAME="):
                    return line.split("=", 1)[1].strip().strip('"')
    except OSError:
        pass
    return f"{platform.system()} {platform.release()}"


def cpu_model():
    try:
        with open("/proc/cpuinfo") as f:
            for line in f:
                if line.lower().startswith(("model name", "hardware")):
                    return line.split(":", 1)[1].strip()
    except OSError:
        pass
    return platform.processor() or platform.machine()


def collect():
    vm, sw = psutil.virtual_memory(), psutil.swap_memory()
    disk_path = HOST_ROOT if os.path.exists(HOST_ROOT) else "/"
    du = psutil.disk_usage(disk_path)
    gname, gtotal, gutil, gused = gpu()
    return {
        "name": os.environ.get("NODE_NAME"),
        "nodeType": os.environ.get("NODE_TYPE"),
        "location": os.environ.get("NODE_LOCATION"),
        "specs": {
            "osInfo": os_info(),
            "cpuModel": cpu_model(),
            "cpuCores": psutil.cpu_count(),
            "memoryTotalMb": vm.total // MB,
            "gpuModel": gname,
            "gpuMemoryMb": gtotal,
            "diskTotalGb": du.total // (1024 ** 3),
        },
        "cpuPct": psutil.cpu_percent(interval=1),
        "memUsedMb": (vm.total - vm.available) // MB,
        "memAvailableMb": vm.available // MB,
        "memPct": vm.percent,
        "swapUsedMb": sw.used // MB,
        "diskUsedGb": round(du.used / 1024 ** 3, 1),
        "diskPct": du.percent,
        "gpuUtilPct": gutil,
        "gpuMemUsedMb": gused,
        "loadAvg1m": os.getloadavg()[0],
        "uptimeSec": int(time.time() - psutil.boot_time()),
        "extra": None,
        "processes": processes() + containers(),
    }


def push(payload):
    req = urllib.request.Request(
        f"{URL}/ingest/{NODE_KEY}", data=json.dumps(payload).encode(), method="POST",
        headers={"Authorization": f"Bearer {TOKEN}", "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=20) as r:
        return r.status


def main():
    if not TOKEN:
        raise SystemExit("INFRA_TOKEN 이 필요합니다 (d94_infra_... WRITE 토큰)")
    for p in psutil.process_iter():  # cpu_percent 기준점
        try:
            p.cpu_percent(interval=None)
        except (psutil.NoSuchProcess, psutil.AccessDenied):
            pass
    print(f"d94-infra-agent node={NODE_KEY} url={URL} every {INTERVAL}s", flush=True)
    while True:
        started = time.time()
        try:
            push(collect())
        except Exception as e:  # noqa: BLE001 — 한 번 실패해도 다음 주기에 다시
            print("push failed:", e, flush=True)
        time.sleep(max(1, INTERVAL - (time.time() - started)))


if __name__ == "__main__":
    if os.environ.get("AGENT_SELFTEST"):
        assert redact("java --password hunter2 -jar x") == "java --password *** -jar x"
        assert redact("X API_KEY=abc Y") == "X API_KEY=*** Y"
        assert redact("node server.js") == "node server.js"
        print(json.dumps({k: v for k, v in collect().items() if k != "processes"}, indent=1))
        print("selftest ok")
    else:
        main()
