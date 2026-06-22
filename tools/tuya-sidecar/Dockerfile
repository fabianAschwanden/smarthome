# Tuya/Midea/Gecko-Sidecar – kleiner Python-HTTP-Dienst (siehe sidecar.py).
# Liest Tuya-3.4/3.5-, Midea/NetHome-Plus- und Gecko-Geräte im LAN; vom Backend genutzt.
# Python 3.13+ nötig: geckolib 1.0.x verlangt >=3.13.
FROM python:3.13-slim

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
