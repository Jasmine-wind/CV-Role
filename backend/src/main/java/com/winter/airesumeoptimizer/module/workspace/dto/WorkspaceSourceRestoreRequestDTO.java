package com.winter.airesumeoptimizer.module.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 请求恢复一段冻结 SOURCE occurrence 对应的原文节点。
 *
 * <p>客户端只能表达“我要恢复这个 frozen SOURCE occurrence”；恢复什么节点、恢复到哪、边界多大
 * 全部由服务端按冻结 manifest 与认证归属重新解析，请求不接受 targetNodeId、sectionId、文本、
 * provenance 或任何节点 JSON。
 */
@Schema(description = "按冻结来源 occurrence 请求服务端权威的局部原文恢复")
public record WorkspaceSourceRestoreRequestDTO(
        @NotNull(message = "缺少内容版本号")
        @PositiveOrZero(message = "内容版本号不能为负数")
        Long expectedRevision,
        @NotEmpty(message = "来源 occurrence ID 不能为空")
        @Size(max = 500, message = "来源 occurrence ID 数量超出上限")
        List<@NotNull(message = "来源 occurrence ID 不能为空") String> sourceOccurrenceIds) {
}
