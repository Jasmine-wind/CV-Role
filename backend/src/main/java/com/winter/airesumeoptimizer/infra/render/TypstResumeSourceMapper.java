package com.winter.airesumeoptimizer.infra.render;

import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBasicsDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBulletDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentContactDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentEntryDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentSectionDTO;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 把结构化简历文档确定性地映射为 Typst 数据文件。
 *
 * <p>安全边界：所有用户内容只会以 Typst 字符串字面量形式出现，字符串之外的任何 Typst
 * 语法（markup、代码、import、网络或文件读取）都不可能由用户内容触发，从根上杜绝
 * Typst 注入。同一份文档的映射结果逐字节确定，Preview 与 Export 共享同一映射。
 */
@Component
public class TypstResumeSourceMapper {

    /** 生成数据文件内容：仅包含一个 `resume` 字典，全部值为转义后的字符串字面量。 */
    public String mapToDataSource(ResumeDocumentDTO document) {
        if (document == null) {
            throw new ResumeRenderException("简历内容为空，无法渲染");
        }
        StringBuilder source = new StringBuilder(4096);
        source.append("// Auto-generated render data. All user content is escaped string literals.\n");
        source.append("#let resume = (\n");
        source.append("  schema: ").append(toTypstString(ResumeDocumentDTO.SCHEMA_VERSION)).append(",\n");
        source.append("  basics: ").append(mapBasics(document.getBasics())).append(",\n");
        source.append("  sections: ").append(mapSections(document.getSections())).append(",\n");
        source.append(")\n");
        return source.toString();
    }

    private String mapBasics(ResumeDocumentBasicsDTO basics) {
        StringBuilder builder = new StringBuilder();
        builder.append("(\n");
        builder.append("    name: ").append(toTypstString(basics == null ? null : basics.getName())).append(",\n");
        builder.append("    job-intention: ")
                .append(toTypstString(basics == null ? null : basics.getJobIntention())).append(",\n");
        builder.append("    highest-education: ")
                .append(toTypstString(basics == null ? null : basics.getHighestEducation())).append(",\n");
        builder.append("    contacts: ");
        List<ResumeDocumentContactDTO> contacts = basics == null ? null : basics.getContacts();
        if (contacts == null || contacts.isEmpty()) {
            builder.append("()");
        } else {
            builder.append("(\n");
            for (ResumeDocumentContactDTO contact : contacts) {
                builder.append("      (type: ")
                        .append(toTypstString(contact == null ? null : contact.getType()))
                        .append(", label: ")
                        .append(toTypstString(contact == null ? null : contact.getLabel()))
                        .append(", value: ")
                        .append(toTypstString(contact == null ? null : contact.getValue()))
                        .append("),\n");
            }
            builder.append("    )");
        }
        builder.append(",\n  )");
        return builder.toString();
    }

    private String mapSections(List<ResumeDocumentSectionDTO> sections) {
        if (sections == null || sections.isEmpty()) {
            return "()";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("(\n");
        for (ResumeDocumentSectionDTO section : sections) {
            if (section == null) {
                continue;
            }
            builder.append("    (\n");
            builder.append("      kind: ").append(toTypstString(section.getKind())).append(",\n");
            builder.append("      title: ").append(toTypstString(section.getTitle())).append(",\n");
            builder.append("      entries: ").append(mapEntries(section.getEntries())).append(",\n");
            builder.append("    ),\n");
        }
        builder.append("  )");
        return builder.toString();
    }

    private String mapEntries(List<ResumeDocumentEntryDTO> entries) {
        if (entries == null || entries.isEmpty()) {
            return "()";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("(\n");
        for (ResumeDocumentEntryDTO entry : entries) {
            if (entry == null) {
                continue;
            }
            builder.append("        (\n");
            builder.append("          organization: ").append(toTypstString(entry.getOrganization())).append(",\n");
            builder.append("          role: ").append(toTypstString(entry.getRole())).append(",\n");
            builder.append("          school: ").append(toTypstString(entry.getSchool())).append(",\n");
            builder.append("          degree: ").append(toTypstString(entry.getDegree())).append(",\n");
            builder.append("          major: ").append(toTypstString(entry.getMajor())).append(",\n");
            builder.append("          start-date: ").append(toTypstString(entry.getStartDate())).append(",\n");
            builder.append("          end-date: ").append(toTypstString(entry.getEndDate())).append(",\n");
            builder.append("          location: ").append(toTypstString(entry.getLocation())).append(",\n");
            builder.append("          group: ").append(toTypstString(entry.getGroup())).append(",\n");
            builder.append("          skill-items: ").append(mapSkillItems(entry.getSkillItems())).append(",\n");
            builder.append("          bullets: ").append(mapBullets(entry.getBullets())).append(",\n");
            builder.append("        ),\n");
        }
        builder.append("      )");
        return builder.toString();
    }

    private String mapSkillItems(List<String> skillItems) {
        if (skillItems == null || skillItems.isEmpty()) {
            return "()";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("(\n");
        for (String item : skillItems) {
            builder.append("            ")
                    .append(toTypstString(item))
                    .append(",\n");
        }
        builder.append("          )");
        return builder.toString();
    }

    private String mapBullets(List<ResumeDocumentBulletDTO> bullets) {
        if (bullets == null || bullets.isEmpty()) {
            return "()";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("(\n");
        for (ResumeDocumentBulletDTO bullet : bullets) {
            builder.append("            ")
                    .append(toTypstString(bullet == null ? null : bullet.getText()))
                    .append(",\n");
        }
        builder.append("          )");
        return builder.toString();
    }

    /**
     * 生成 Typst 字符串字面量：null 归一为空串，反斜杠、引号、换行与控制字符全部转义，
     * 其他字符原样保留；返回值总是以双引号包裹的合法 Typst 字符串。
     */
    public static String toTypstString(String value) {
        StringBuilder builder = new StringBuilder(value == null ? 2 : value.length() + 2);
        builder.append('"');
        if (value != null) {
            int index = 0;
            while (index < value.length()) {
                int codePoint = value.codePointAt(index);
                switch (codePoint) {
                    case '\\' -> builder.append("\\\\");
                    case '"' -> builder.append("\\\"");
                    case '\n' -> builder.append("\\n");
                    case '\r' -> builder.append("\\r");
                    case '\t' -> builder.append("\\t");
                    default -> {
                        if (codePoint < 0x20 || codePoint == 0x7F) {
                            builder.append("\\u{").append(Integer.toHexString(codePoint)).append("}");
                        } else {
                            builder.appendCodePoint(codePoint);
                        }
                    }
                }
                index += Character.charCount(codePoint);
            }
        }
        builder.append('"');
        return builder.toString();
    }
}
