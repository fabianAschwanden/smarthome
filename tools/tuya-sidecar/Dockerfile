# Tuya/Midea/Gecko-Sidecar – kleiner Python-HTTP-Dienst (siehe sidecar.py).
# Liest Tuya-3.4/3.5-, Midea/NetHome-Plus- und Gecko-Geräte im LAN; vom Backend genutzt.
# Python 3.13+ nötig: geckolib 1.0.x verlangt >=3.13.
FROM python:3.13-slim

WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY sidecar.py ./

# Default bewusst Loopback: der Sidecar hat KEINE eigene Auth. Backend und Sidecar teilen
# sich das Host-Netz (network_mode: host bzw. hostNetwork) und erreichen sich darüber.
# Wer ihn wirklich exponieren muss, setzt HOST explizit im Deployment.
ENV HOST=127.0.0.1
ENV PORT=8765
EXPOSE 8765

# Unprivilegiert laufen: der Prozess braucht keine Root-Rechte (nur ausgehende Sockets).
RUN useradd --system --uid 10001 --no-create-home sidecar
USER 10001
CMD ["python", "sidecar.py"]
