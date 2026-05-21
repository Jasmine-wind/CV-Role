# Nginx Draft

This directory contains a reverse proxy draft for the AI Resume Optimizer deployment path.

Current scope:

- Serve built frontend files from `/var/www/ai-resume-optimizer/web`.
- Proxy `/api/` to the Spring Boot backend at `http://127.0.0.1:8080`.
- Keep Vue Router history fallback through `try_files`.
- Reserve Swagger / OpenAPI proxy paths for deployment debugging.
- Reserve ACME challenge paths for Certbot issuance.

Before using on a real server:

- Replace `server_name example.com` with the actual domain or server IP.
- Replace `root /var/www/ai-resume-optimizer/web` with the real frontend `dist` deployment directory.
- Build frontend with production API base URL, usually `VITE_API_BASE_URL=/api`.
- Use `deploy/nginx/conf.d/ai-resume-https.conf` after certificate issuance.
- `docker-compose.prod.yml` already includes `certbot` and the related volumes.
- Do not expose backend port `8080` directly to the public network.
