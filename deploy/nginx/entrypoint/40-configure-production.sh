#!/usr/bin/env sh
set -eu

: "${DEPLOY_DOMAIN:?DEPLOY_DOMAIN is required}"
case "$DEPLOY_DOMAIN" in
  *[!A-Za-z0-9.-]*|'')
    echo "DEPLOY_DOMAIN must be a hostname" >&2
    exit 1
    ;;
esac

template=/etc/nginx/production/ai-resume.prod.conf.template
target=/etc/nginx/conf.d/default.conf
cert_dir="/etc/letsencrypt/live/$DEPLOY_DOMAIN"

if [ ! -r "$template" ]; then
  echo "Production Nginx template not found: $template" >&2
  exit 1
fi

if [ -s "$cert_dir/fullchain.pem" ] && [ -s "$cert_dir/privkey.pem" ]; then
  selected=cert
else
  selected=no_cert
fi

sed "s/__DEPLOY_DOMAIN__/$DEPLOY_DOMAIN/g" "$template" | awk -v selected="$selected" '
  /# CV_ROLE_NO_CERT_BEGIN/ { block = "no_cert"; next }
  /# CV_ROLE_NO_CERT_END/ { block = ""; next }
  /# CV_ROLE_CERT_BEGIN/ { block = "cert"; next }
  /# CV_ROLE_CERT_END/ { block = ""; next }
  block == selected { print }
' > "$target"

chmod 0644 "$target"
