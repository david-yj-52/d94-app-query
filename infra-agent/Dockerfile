FROM python:3.12-slim
RUN pip install --no-cache-dir psutil==6.1.1 paho-mqtt==2.1.0
WORKDIR /app
COPY agent.py mqtt_bridge.py ./
ENV PYTHONUNBUFFERED=1
CMD ["python", "agent.py"]
