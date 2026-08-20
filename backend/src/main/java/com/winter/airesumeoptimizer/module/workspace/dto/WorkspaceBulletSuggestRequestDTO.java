package com.winter.airesumeoptimizer.module.workspace.dto;

import com.winter.airesumeoptimizer.module.workspace.enums.BulletSuggestIntent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 单 Bullet AI 改写建议请求。
 *
 * <p>生命周期绑定字段：requestId（客户端生成，用于乱序/并发判别）、baseRevision（发起时
 * TARGET 内容版本号）、bulletId（用户明确选中的要点，不做模糊查找）、originalTextHash
 * （原文 SHA-256，判别 Bullet 是否被人工编辑）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "单 Bullet 岗位定向改写建议请求")
public class WorkspaceBulletSuggestRequestDTO {

    @NotBlank(message = "缺少请求 ID")
    @Size(max = 64, message = "请求 ID 过长")
    @Schema(description = "客户端生成的请求 UUID，原样回传用于乱序判别")
    private String requestId;

    @NotBlank(message = "缺少要点 ID")
    @Size(max = 128, message = "要点 ID 过长")
    @Schema(description = "用户明确选中的 Bullet ID，服务端只做精确匹配")
    private String bulletId;

    @NotNull(message = "缺少内容版本号")
    @PositiveOrZero(message = "内容版本号不能为负数")
    @Schema(description = "发起建议时前端已持久化的 TARGET 内容版本号")
    private Long baseRevision;

    @NotBlank(message = "缺少要点原文")
    @Size(max = 4000, message = "要点原文过长")
    @Schema(description = "发起建议时 Bullet 的文本，用于一致性交叉校验")
    private String originalText;

    @NotBlank(message = "缺少要点原文哈希")
    @Size(max = 128, message = "要点原文哈希过长")
    @Schema(description = "原文去除首尾空白后的 SHA-256 十六进制值")
    private String originalTextHash;

    @NotNull(message = "缺少改写意图")
    @Schema(description = "改写意图")
    private BulletSuggestIntent intent;

    @Size(max = 500, message = "本次要求过长")
    @Schema(description = "用户本次自定义要求，视为不可信输入")
    private String userInstruction;
}
