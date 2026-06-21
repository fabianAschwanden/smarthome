# Login-Proxy fürs Remote-Gateway: oauth2-proxy (OIDC) als Reverse-Proxy zum Heim-Server.
#
# Das oauth2-proxy-Image ist distroless (KEIN /bin/sh) – ein Shell-entrypoint würde
# mit "No such file or directory" scheitern (fehlender Interpreter). Darum holen wir
# eine statische busybox dazu und nutzen sie direkt als /bin/sh. Das entrypoint.sh
# kommt mit reinem POSIX-sh aus (keine extra Coreutils nötig).
FROM busybox:1.37-uclibc AS shell

FROM quay.io/oauth2-proxy/oauth2-proxy:v7.7.1

# busybox ist statisch gelinkt und funktioniert als sh, wenn es so aufgerufen wird.
COPY --from=shell /bin/busybox /bin/sh

COPY entrypoint.sh /entrypoint.sh
ENTRYPOINT ["/bin/sh", "/entrypoint.sh"]
