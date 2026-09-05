FROM maven@sha256:3a4ab3276a087bf276f79cae96b1af04f53731bec53fb2e651aca79e4b10211e AS build

WORKDIR /workspace

COPY backend/pom.xml backend/pom.xml
COPY backend/src backend/src

RUN mvn -f backend/pom.xml -DskipTests package

FROM eclipse-temurin@sha256:18d5699313d4ba48c1e2d46a56f7d9a760bac61eec826456e0bb675dc72edd76 AS runtime

WORKDIR /app

# Phase 6 渲染依赖：Typst 编译器与 CJK 字体。Preview 与 Export 共享同一镜像内的
# 编译器与字体环境，保证所见即所得；typst 版本升级必须同步验证模板与渲染器版本。
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates xz-utils fonts-noto-cjk \
    && mkdir -p /opt/resume-fonts \
    && find /usr/share/fonts -type f \( \
        -name 'NotoSansCJK-Regular.ttc' \
        -o -name 'NotoSansCJK-Bold.ttc' \
    \) -exec cp {} /opt/resume-fonts/ \; \
    && test -f /opt/resume-fonts/NotoSansCJK-Regular.ttc \
    && test -f /opt/resume-fonts/NotoSansCJK-Bold.ttc \
    && curl -fsSL https://github.com/typst/typst/releases/download/v0.15.1/typst-x86_64-unknown-linux-musl.tar.xz -o /tmp/typst.tar.xz \
    && echo 'a6d077d0a95eed5a2eba715b2dae06be954f624ccbf85758a03f389ded33118c  /tmp/typst.tar.xz' | sha256sum -c - \
    && tar -xJf /tmp/typst.tar.xz -C /tmp \
    && mv /tmp/typst-x86_64-unknown-linux-musl/typst /usr/local/bin/typst \
    && chmod +x /usr/local/bin/typst \
    && rm -rf /tmp/typst.tar.xz /tmp/typst-x86_64-unknown-linux-musl /var/lib/apt/lists/*

# Use only the copied static CJK family at runtime; this prevents host font fallback from
# changing the weight selected by Preview and Export.
ENV APP_RENDER_FONT_PATH=/opt/resume-fonts

COPY --from=build /workspace/backend/target/ai-resume-optimizer-backend-0.1.0-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

