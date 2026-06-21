# Login-Proxy fürs Remote-Gateway: oauth2-proxy (OIDC) als Reverse-Proxy zum Heim-Server.
FROM quay.io/oauth2-proxy/oauth2-proxy:v7.7.1

COPY entrypoint.sh /entrypoint.sh
# (Image-User ist non-root; entrypoint.sh ist im Repo bereits ausführbar.)
ENTRYPOINT ["/entrypoint.sh"]
