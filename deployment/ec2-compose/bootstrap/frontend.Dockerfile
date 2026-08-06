# syntax=docker/dockerfile:1.12

ARG NODE_IMAGE=node:24.18.0-alpine3.23@sha256:00295958df3ca22b4d3df4ae8dbffd823dbca1c53efbcf235f62fbf3fa5ca756
ARG NGINX_IMAGE=nginx:1.28.3-alpine3.23@sha256:0dcc88822d45581e65ae329f8be769762bf628d3b2bb7d2a077d4aa5c98b30e3

FROM ${NODE_IMAGE} AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY index.html webpack.config.js ./
COPY src ./src
RUN npm run build:react && find dist -type f -name '*.map' -delete

FROM ${NGINX_IMAGE} AS nginx-rootfs
RUN rm -f /etc/nginx/conf.d/default.conf
COPY .bootstrap-nginx.conf /etc/nginx/nginx.conf

FROM scratch AS runtime
ARG OCI_SOURCE=https://github.com/BS-Stack-Lab/KTB4-ian-community-FE
ARG OCI_REVISION=bootstrap
ARG OCI_VERSION=bootstrap
LABEL org.opencontainers.image.source="${OCI_SOURCE}" \
      org.opencontainers.image.revision="${OCI_REVISION}" \
      org.opencontainers.image.version="${OCI_VERSION}"
COPY --from=nginx-rootfs / /
COPY --from=builder --chown=nginx:nginx /app/index.html /usr/share/nginx/html/index.html
COPY --from=builder --chown=nginx:nginx /app/dist /usr/share/nginx/html/dist
ENV PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
USER 101:101
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://127.0.0.1:8080/healthz || exit 1
STOPSIGNAL SIGQUIT
CMD ["/usr/sbin/nginx", "-g", "daemon off;"]
