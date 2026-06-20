# Tuya/Midea-Sidecar – kleiner Python-HTTP-Dienst (siehe sidecar.py).
# Liest Tuya-3.4/3.5- und Midea/NetHome-Plus-Geräte im LAN; vom Quarkus-Backend genutzt.
FROM python:3.12-slim

WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY sidecar.py ./

# Im Compose über das Host-Netz erreichbar; bindet an alle Interfaces statt nur localhost,
# damit das Backend (anderer Container/Host) ihn erreicht.
ENV HOST=0.0.0.0
ENV PORT=8765
EXPOSE 8765
CMD ["python", "sidecar.py"]
