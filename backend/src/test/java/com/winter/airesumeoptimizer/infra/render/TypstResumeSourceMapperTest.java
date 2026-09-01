package com.winter.airesumeoptimizer.infra.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBasicsDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBulletDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentContactDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentEntryDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentSectionDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 映射器安全基线：所有用户内容只能以转义字符串字面量出现，
 * 任何 Typst 代码/markup 语法都不能由用户内容逃逸触发。
 */
class TypstResumeSourceMapperTest {

    private final TypstResumeSourceMapper mapper = new TypstResumeSourceMapper();

    @Test
    void toTypstStringEscapesQuotesBackslashesAndControlChars() {
        assertThat(TypstResumeSourceMapper.toTypstString(null)).isEqualTo("\"\"");
        assertThat(TypstResumeSourceMapper.toTypstString("")).isEqualTo("\"\"");
        assertThat(TypstResumeSourceMapper.toTypstString("普通文本")).isEqualTo("\"普通文本\"");
        assertThat(TypstResumeSourceMapper.toTypstString("a\"b")).isEqualTo("\"a\\\"b\"");
        assertThat(TypstResumeSourceMapper.toTypstString("a\\b")).isEqualTo("\"a\\\\b\"");
        assertThat(TypstResumeSourceMapper.toTypstString("a\nb\rc\td"))
                .isEqualTo("\"a\\nb\\rc\\td\"");
        assertThat(TypstResumeSourceMapper.toTypstString("a\u0000b\u001bc"))
                .isEqualTo("\"a\\u{0}b\\u{1b}c\"");
    }

    @Test
    void injectionAttemptsRemainInsideStringLiterals() {
        String malicious = "\" ] #import \"evil.typ\": *\n#read(\"/etc/passwd\")\n#import \"@preview/x:1\" *(end)";
        String literal = TypstResumeSourceMapper.toTypstString(malicious);

        assertThat(literal).startsWith("\"").endsWith("\"");
        String body = literal.substring(1, literal.length() - 1);
        // 字面量内部不允许出现未转义的双引号，否则字符串会提前闭合导致语法逃逸。
        for (int index = 0; index < body.length(); index++) {
            if (body.charAt(index) == '"') {
                assertThat(index).isGreaterThan(0);
                assertThat(body.charAt(index - 1)).isEqualTo('\\');
            }
        }
        assertThat(body).doesNotContain("\n").doesNotContain("\r");
    }

    @Test
    void mapToDataSourceOnlyEmitsEscapedLiteralsForUserContent() {
        ResumeDocumentDTO document = documentWithBullet(
                "负责 \"核心\" 模块 #read(\"/etc/passwd\") ] 换行\n注入");

        String source = mapper.mapToDataSource(document);

        assertThat(source).contains("#let resume = (");
        // 注入片段必须以转义字面量出现，不能被拆成独立 Typst 语句。
        assertThat(source).contains(
                "\"负责 \\\"核心\\\" 模块 #read(\\\"/etc/passwd\\\") ] 换行\\n注入\"");
        // 数据文件中除模板生成的 let 绑定外没有其它代码入口。
        assertThat(countOccurrences(source, "#let")).isEqualTo(1);
        // 注入片段必须整体位于单个字符串字面量内：其所在行以引号开始并在同一行闭合，
        // 换行已被转义，无法拆出独立的 Typst 语句。
        String bulletLine = source.lines()
                .filter(line -> line.contains("/etc/passwd"))
                .findFirst()
                .orElseThrow();
        assertThat(bulletLine.strip()).startsWith("\"").endsWith("\",");
    }

    @Test
    void mapToDataSourceIsDeterministic() {
        ResumeDocumentDTO document = documentWithBullet("同一条内容");
        assertThat(mapper.mapToDataSource(document))
                .isEqualTo(mapper.mapToDataSource(document));
    }

    @Test
    void mapToDataSourceHandlesMissingOptionalFields() {
        ResumeDocumentDTO document = ResumeDocumentDTO.builder()
                .schemaVersion(ResumeDocumentDTO.SCHEMA_VERSION)
                .basics(ResumeDocumentBasicsDTO.builder().name(null).contacts(null).build())
                .sections(List.of(ResumeDocumentSectionDTO.builder()
                        .id("s-1")
                        .kind("CUSTOM")
                        .title(null)
                        .entries(List.of(ResumeDocumentEntryDTO.builder()
                                .id("e-1")
                                .organization(null)
                                .startDate(null)
                                .bullets(null)
                                .build()))
                        .build()))
                .build();

        String source = mapper.mapToDataSource(document);

        assertThat(source).contains("name: \"\"");
        assertThat(source).contains("contacts: ()");
        assertThat(source).contains("bullets: ()");
    }

    @Test
    void mapToDataSourceRejectsNullDocument() {
        assertThatThrownBy(() -> mapper.mapToDataSource(null))
                .isInstanceOf(ResumeRenderException.class);
    }

    private ResumeDocumentDTO documentWithBullet(String bulletText) {
        return ResumeDocumentDTO.builder()
                .schemaVersion(ResumeDocumentDTO.SCHEMA_VERSION)
                .basics(ResumeDocumentBasicsDTO.builder()
                        .name("张三")
                        .contacts(List.of(ResumeDocumentContactDTO.builder()
                                .id("c-1").type("PHONE").label("电话").value("13800000000").build()))
                        .build())
                .sections(List.of(ResumeDocumentSectionDTO.builder()
                        .id("s-1")
                        .kind("EXPERIENCE")
                        .title("工作经历")
                        .entries(List.of(ResumeDocumentEntryDTO.builder()
                                .id("e-1")
                                .organization("某科技有限公司")
                                .startDate("2022.07")
                                .endDate("至今")
                                .bullets(List.of(ResumeDocumentBulletDTO.builder()
                                        .id("b-1").text(bulletText).build()))
                                .build()))
                        .build()))
                .build();
    }

    private int countOccurrences(String source, String token) {
        int count = 0;
        int index = source.indexOf(token);
        while (index >= 0) {
            count++;
            index = source.indexOf(token, index + token.length());
        }
        return count;
    }
}
