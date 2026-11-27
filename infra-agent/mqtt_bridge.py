"""ESP32 → infra 브리지. smartfarm/esp32/{id}/telemetry 를 구독해 /ingest/esp32-{id} 로 전달.

env: INFRA_URL, INFRA_TOKEN (agent.py 와 같음), MQTT_HOST(기본 192.168.1.74), MQTT_PORT(1883),
     MQTT_USERNAME/MQTT_PASSWORD (브로커는 익명 불가 — 읽기 전용 계정 d94-infra-bridge),
     MQTT_TOPIC(기본 smartfarm/esp32/+/telemetry), MIN_INTERVAL_SEC(장비당 최소 전송 간격, 기본 60)

telemetry 본문(JSON) 예: {"free_heap": 182344, "rssi": -61, "uptime": 3600}
"""
import json
import os
import time
import urllib.request

import paho.mqtt.client as mqtt

URL = os.environ.get("INFRA_URL", "https://api.tongsung.site/tsh/api/v1/infra").rstrip("/")
TOKEN = os.environ["INFRA_TOKEN"]
MIN_INTERVAL = int(os.environ.get("MIN_INTERVAL_SEC", "60"))
_last = {}


def to_ingest(device, t):
    heap = t.get("free_heap")
    return {
        "name": f"ESP32 {device}",
        "nodeType": "MCU",
        "location": "HOME_LAN",
        "uptimeSec": t.get("uptime"),
        "extra": {"free_heap_kb": round(heap / 1024, 1) if heap is not None else None,
                  "wifi_rssi": t.get("rssi"), "raw": t},
        "processes": [],
    }


def on_message(_c, _u, msg):
    parts = msg.topic.split("/")
    if len(parts) < 4:
        return
    device = parts[2]
    now = time.time()
    if now - _last.get(device, 0) < MIN_INTERVAL:
        return
    try:
        body = to_ingest(device, json.loads(msg.payload))
        req = urllib.request.Request(
            f"{URL}/ingest/esp32-{device}", data=json.dumps(body).encode(), method="POST",
            headers={"Authorization": f"Bearer {TOKEN}", "Content-Type": "application/json"})
        urllib.request.urlopen(req, timeout=20).close()
        _last[device] = now
    except Exception as e:  # noqa: BLE001
        print("forward failed:", device, e, flush=True)


def on_connect(cl, _u, _f, rc, _p):
    print("mqtt connect:", rc, flush=True)  # 인증 실패면 "Not authorized"
    if rc == 0:
        cl.subscribe(os.environ.get("MQTT_TOPIC", "smartfarm/esp32/+/telemetry"))


def main():
    c = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="d94-infra-bridge")
    if os.environ.get("MQTT_USERNAME"):
        c.username_pw_set(os.environ["MQTT_USERNAME"], os.environ.get("MQTT_PASSWORD"))
    c.on_connect = on_connect
    c.on_message = on_message
    c.connect(os.environ.get("MQTT_HOST", "192.168.1.74"), int(os.environ.get("MQTT_PORT", "1883")))
    print("d94-infra-bridge listening", flush=True)
    c.loop_forever(retry_first_connection=True)


if __name__ == "__main__":
    main()
