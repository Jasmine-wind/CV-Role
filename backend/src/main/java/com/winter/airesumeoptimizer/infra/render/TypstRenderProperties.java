package com.winter.airesumeoptimizer.infra.render;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.render")
public class TypstRenderProperties {

    /** Typst 可执行文件；生产镜像内置，本地开发依赖系统安装。 */
    private String typstBinary = "typst";

    /** 单次同步编译超时；超时即终止进程，Preview / Export 返回可重试错误。 */
    private Duration timeout = Duration.ofSeconds(30);

    /**
     * 可选的固定字体目录。生产镜像提供只包含审核过的 CJK 静态字重的目录；
     * 为空时保留本地系统字体发现，便于开发机运行。
     */
    private String fontPath = "";
}
