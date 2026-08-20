package com.winter.airesumeoptimizer.module.workspace.service.impl;

import com.winter.airesumeoptimizer.module.workspace.dto.RewriteFactValidationResult;
import com.winter.airesumeoptimizer.module.workspace.enums.RewriteFactViolationCode;
import com.winter.airesumeoptimizer.module.workspace.service.RewriteFactValidator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * 保守的确定性事实校验器：只依赖代码规则，不调用第二次 LLM。
 *
 * <p>事实基线是 Bullet 原文本身（当前 TARGET Bullet 是用户已明确输入的事实来源），
 * 不引入其它 Bullet、SOURCE 或 Evidence 的文本，从机制上禁止跨 Bullet 搬运事实。
 * 允许同义改写、语法调整与不改变事实的语言重组；任何规则无法确认为“无事实扩张”的情况一律拒绝。
 */
@Service
public class RewriteFactValidatorImpl implements RewriteFactValidator {

    static final int MAX_SUGGESTED_LENGTH = 4000;

    /** 数字与小数：任何原文没有的数字都视为新增量化事实。 */
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+(?:[.,]\\d+)*");

    /** 中文量化表达：三倍、百分之三十、百万等；新增即拒绝。 */
    private static final Pattern CHINESE_QUANT_PATTERN = Pattern.compile(
            "百分之[一二两三四五六七八九十百]+"
                    + "|[一二两三四五六七八九十百千万亿零几数]+(?:倍|成|万|亿|千|百)");

    /** 年份与日期：原文没有的年份/日期是新增时间事实。 */
    private static final Pattern DATE_PATTERN =
            Pattern.compile("\\d{4}\\s*年|\\d{4}\\s*[./\\-]\\s*\\d{1,2}");

    /** 拉丁技术/实体 token，在 NFKC 归一化后的原文与建议文本上提取。 */
    private static final Pattern LATIN_TOKEN_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9+#.\\-]*");

    /** 疑似元素 ID / UUID：AI 不得生成结构化身份。 */
    private static final Pattern ELEMENT_ID_PATTERN = Pattern.compile(
            "(?:^|[^A-Za-z0-9])((?:s|e|b|c|ent|sec)-\\d+|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})(?:[^A-Za-z0-9]|$)");

    /** 无数字的倍数 / 量级声明。 */
    private static final List<String> MULTIPLIER_MARKERS = List.of(
            "翻倍", "翻番", "数倍", "提升数倍", "数倍提升", "量级提升", "指数级");

    /** 常见技术词（小写拉丁）：建议文本新增即拒绝。 */
    private static final Set<String> LATIN_TECH_TERMS = Set.of(
            "redis", "mysql", "mariadb", "postgresql", "postgres", "oracle", "sqlserver", "mongodb",
            "elasticsearch", "solr", "kafka", "rabbitmq", "rocketmq", "activemq", "pulsar", "zookeeper",
            "nacos", "eureka", "consul", "dubbo", "spring", "springboot", "springcloud", "springmvc",
            "mybatis", "hibernate", "jpa", "netty", "tomcat", "jetty", "undertow", "jvm", "java",
            "kotlin", "scala", "golang", "rust", "python", "django", "flask", "php", "javascript",
            "typescript", "nodejs", "node.js", "vue", "react", "angular", "webpack", "vite", "rollup",
            "nginx", "apache", "docker", "kubernetes", "k8s", "helm", "istio", "jenkins", "gitlab",
            "github", "maven", "gradle", "linux", "unix", "hadoop", "spark", "flink", "storm", "hive",
            "hbase", "clickhouse", "tidb", "doris", "presto", "minio", "aws", "azure", "aliyun",
            "grpc", "thrift", "webservice", "websocket", "graphql", "oauth", "oauth2", "jwt", "sso",
            "sharding", "nosql", "newsql", "prometheus", "grafana", "skywalking", "zipkin", "sentry",
            "tensorflow", "pytorch", "llm", "aigc", "rag", "docker-compose", "cicd", "devops",
            "saas", "paas", "middleware", "sdk", "openapi", "restful", "microservice", "microservices",
            "memcached", "etcd", "terraform", "ansible", "trino", "rocksdb", "sentinel", "cassandra",
            "influxdb", "neo4j", "redshift", "snowflake", "databricks", "airflow", "opensearch",
            "lucene", "hudi", "iceberg", "oceanbase", "polardb", "envoy", "traefik", "kong", "apisix",
            "wasm", "webassembly", "junit", "mockito", "selenium", "cmake", "llvm", "rxjava",
            "langchain", "ollama", "milvus", "faiss", "pinia", "nuxt", "nextjs", "vitepress");

    /** 中文技术 / 能力名词：建议文本新增即拒绝（原文已有的表达允许保留或删减）。 */
    private static final List<String> CHINESE_TECH_TERMS = List.of(
            "微服务", "服务网格", "消息队列", "分布式", "高并发", "高可用", "高性能", "分库分表", "读写分离",
            "机器学习", "深度学习", "大模型", "大语言模型", "人工智能", "智能体", "推荐算法", "推荐系统",
            "搜索引擎", "检索增强", "知识库", "知识图谱", "向量", "数据仓库",
            "数据湖", "实时计算", "流式计算", "容器化", "云原生", "中台", "灰度发布", "熔断", "限流",
            "负载均衡", "性能调优", "全链路压测", "链路追踪", "异地多活", "容灾", "数据治理");

    /** 范围事实标记之外的新增汉字连续段判定所用词根：出现即视为技术语境。 */
    private static final List<String> TECH_HAN_ROOTS = List.of(
            "框架", "架构", "引擎", "组件", "中间件", "数据库", "缓存", "队列", "分布式", "并发",
            "微服务", "容器", "集群", "算法", "模型", "接口", "协议", "服务", "系统", "平台",
            "后端", "前端", "全栈", "网关", "索引", "线程", "内存", "调优", "压测", "灰度",
            "链路", "容灾", "治理", "数仓", "数据", "智能", "云", "运维", "部署", "编译", "内核");

    /** 连续汉字段：用于发现原文中不存在的新技术 / 新事实名词。 */
    private static final Pattern HAN_RUN_PATTERN = Pattern.compile("[\\p{IsHan}]{2,}");

    /** 纯表达层高频词：新增时不视为事实扩张。 */
    private static final Set<String> EXPRESSION_STOP_WORDS = Set.of(
            "负责", "参与", "完成", "实现", "使用", "开发", "维护", "优化", "设计", "编写",
            "工作", "项目", "相关", "进行", "以及", "基于", "采用", "通过", "针对", "围绕",
            "提升", "改善", "完善", "增强", "支持", "保障", "推动", "落地", "经验", "能力",
            "熟悉", "掌握", "了解", "具备", "熟练", "深入", "扎实", "良好", "丰富", "模块",
            "功能", "业务", "需求", "方案", "流程", "质量", "效率", "稳定", "安全", "规范",
            "团队", "协作", "沟通", "文档", "日常", "主要", "核心", "整体", "独立", "承担",
            "推进", "执行", "跟进", "输出", "沉淀", "总结", "分析", "解决", "处理", "定位");

    /** 责任级别标记：数字越大责任越强；建议文本的最高层级不得高于原文。 */
    private static final List<String> TIER3_MARKERS = List.of(
            "主导", "牵头", "主持", "带队", "带领", "负责人", "技术负责人", "团队负责人", "项目负责人",
            "独立负责", "从零到一", "从0到1", "从 0 到 1", "架构师", "统筹", "owner", "leader",
            "team lead", "tech lead");
    private static final List<String> TIER2_MARKERS = List.of(
            "负责", "独立完成", "独立开发", "独立设计", "独立搭建", "独立负责", "设计并实现", "总体设计");
    private static final Pattern MANAGE_PEOPLE_PATTERN =
            Pattern.compile("管理\\s*\\d+\\s*人|带\\s*\\d+\\s*人");

    /** 新增即视为成果事实扩张的标记。 */
    private static final List<String> ACHIEVEMENT_MARKERS = List.of(
            "获奖", "荣获", "奖项", "创新奖", "专利", "论文", "表彰", "冠军", "第一名", "前三名",
            "优秀员工", "晋升", "显著提升", "明显提升", "明显改善", "大幅提高", "大幅提升",
            "大幅降低", "明显提高", "突破性", "零故障", "零事故", "零缺陷", "业内领先", "行业首创", "首创");

    /** 新增即视为范围事实扩张的标记。 */
    private static final List<String> SCOPE_MARKERS = List.of(
            "全公司", "公司级", "集团级", "集团", "部门级", "跨部门", "跨团队", "跨区域",
            "行业领先", "国内领先", "国际领先", "全球", "海外市场");

    @Override
    public RewriteFactValidationResult validate(String originalText, String suggestedText) {
        if (suggestedText == null || suggestedText.isBlank()) {
            return RewriteFactValidationResult.fail(
                    RewriteFactViolationCode.EMPTY_OR_BLANK, "AI 没有给出可用的改写文本");
        }
        if (suggestedText.length() > MAX_SUGGESTED_LENGTH) {
            return RewriteFactValidationResult.fail(
                    RewriteFactViolationCode.OVERSIZED, "AI 改写文本超过要点长度上限");
        }
        String original = originalText == null ? "" : originalText;

        // NFKC 统一全角/半角；大小写只在拉丁 token 分类时使用，包含性比较一律小写。
        String suggestionNfkc = Normalizer.normalize(suggestedText, Normalizer.Form.NFKC);
        String suggestionCorpus = stripWhitespace(normalize(suggestedText));
        String originalCorpus = stripWhitespace(normalize(original));

        RewriteFactValidationResult identityLeak = checkElementIdentityLeak(suggestionNfkc);
        if (identityLeak != null) {
            return identityLeak;
        }
        RewriteFactValidationResult quantitative = checkQuantitative(suggestionCorpus, originalCorpus);
        if (quantitative != null) {
            return quantitative;
        }
        RewriteFactValidationResult latinTokens =
                checkLatinTokens(suggestionNfkc, originalCorpus);
        if (latinTokens != null) {
            return latinTokens;
        }
        RewriteFactValidationResult chineseTech =
                checkNewMarkers(CHINESE_TECH_TERMS, suggestionCorpus, originalCorpus,
                        RewriteFactViolationCode.NEW_TECHNOLOGY, "改写引入了原文没有的技术或能力描述");
        if (chineseTech != null) {
            return chineseTech;
        }
        RewriteFactValidationResult escalation = checkEscalation(suggestionCorpus, originalCorpus);
        if (escalation != null) {
            return escalation;
        }
        RewriteFactValidationResult achievement =
                checkNewMarkers(ACHIEVEMENT_MARKERS, suggestionCorpus, originalCorpus,
                        RewriteFactViolationCode.NEW_ACHIEVEMENT, "改写引入了原文没有的成果或效果结论");
        if (achievement != null) {
            return achievement;
        }
        RewriteFactValidationResult scope =
                checkNewMarkers(SCOPE_MARKERS, suggestionCorpus, originalCorpus,
                        RewriteFactViolationCode.NEW_SCOPE_OR_TIME, "改写引入了原文没有的范围事实");
        if (scope != null) {
            return scope;
        }
        RewriteFactValidationResult novelHan = checkNovelHanTechTokens(suggestionNfkc, originalCorpus);
        if (novelHan != null) {
            return novelHan;
        }
        return RewriteFactValidationResult.pass();
    }

    /**
     * 新增汉字 bigram 若落在技术词根语境中，视为新增中文技术 / 事实描述，fail closed。
     * 表达层高频词（同义改写常用动词 / 形容词）不视为事实扩张。
     */
    private RewriteFactValidationResult checkNovelHanTechTokens(String suggestionNfkc, String originalCorpus) {
        Matcher matcher = HAN_RUN_PATTERN.matcher(suggestionNfkc);
        while (matcher.find()) {
            String run = matcher.group();
            for (int index = 0; index + 2 <= run.length(); index++) {
                String bigram = run.substring(index, index + 2);
                if (originalCorpus.contains(bigram) || EXPRESSION_STOP_WORDS.contains(bigram)) {
                    continue;
                }
                if (isTechBigram(bigram)) {
                    return RewriteFactValidationResult.fail(
                            RewriteFactViolationCode.NEW_TECHNOLOGY,
                            "改写引入了原文没有的技术或事实描述");
                }
            }
        }
        return null;
    }

    private boolean isTechBigram(String bigram) {
        for (String root : TECH_HAN_ROOTS) {
            if (root.equals(bigram) || root.contains(bigram) || bigram.contains(root)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 原文没有的数字、日期、中文量化或倍数声明都视为新增量化事实。
     * 数字按 token 精确匹配而不是子串包含：原文“2023年”不得被解释为允许“20%”。
     */
    private RewriteFactValidationResult checkQuantitative(String suggestionCorpus, String originalCorpus) {
        Matcher originalMatcher = NUMBER_PATTERN.matcher(originalCorpus);
        List<String> originalNumbers = new ArrayList<>();
        while (originalMatcher.find()) {
            if (!isPartOfLatinToken(originalCorpus, originalMatcher.start(), originalMatcher.end())) {
                originalNumbers.add(originalMatcher.group());
            }
        }
        Matcher matcher = NUMBER_PATTERN.matcher(suggestionCorpus);
        while (matcher.find()) {
            if (isPartOfLatinToken(suggestionCorpus, matcher.start(), matcher.end())) {
                // Vue3、HTTP2 等内嵌数字属于技术名，由拉丁 token 规则判定。
                continue;
            }
            if (!originalNumbers.contains(matcher.group())) {
                return RewriteFactValidationResult.fail(
                        RewriteFactViolationCode.NEW_QUANTITATIVE_CLAIM,
                        "改写引入了原文没有的数字或量化结果");
            }
        }
        Matcher chineseQuantMatcher = CHINESE_QUANT_PATTERN.matcher(suggestionCorpus);
        while (chineseQuantMatcher.find()) {
            if (!originalCorpus.contains(chineseQuantMatcher.group())) {
                return RewriteFactValidationResult.fail(
                        RewriteFactViolationCode.NEW_QUANTITATIVE_CLAIM,
                        "改写引入了原文没有的中文量化表述");
            }
        }
        Matcher dateMatcher = DATE_PATTERN.matcher(suggestionCorpus);
        while (dateMatcher.find()) {
            if (!originalCorpus.contains(stripWhitespace(dateMatcher.group()))) {
                return RewriteFactValidationResult.fail(
                        RewriteFactViolationCode.NEW_SCOPE_OR_TIME,
                        "改写引入了原文没有的年份或日期");
            }
        }
        return checkNewMarkers(MULTIPLIER_MARKERS, suggestionCorpus, originalCorpus,
                RewriteFactViolationCode.NEW_QUANTITATIVE_CLAIM, "改写引入了原文没有的倍数或量级声明");
    }

    /**
     * 拉丁 token 规则：
     * 原文已有的词允许保留；新增时，常见技术词 / 技术形态词（含数字、大写）拒绝为新技术，
     * 首字母大写的新词拒绝为新实体，普通小写词视为语言表达放行。
     */
    private RewriteFactValidationResult checkLatinTokens(String suggestionNfkc, String originalCorpus) {
        Matcher matcher = LATIN_TOKEN_PATTERN.matcher(suggestionNfkc);
        while (matcher.find()) {
            String rawToken = matcher.group();
            String token = rawToken.toLowerCase(Locale.ROOT);
            if (originalCorpus.contains(token)) {
                continue;
            }
            if (LATIN_TECH_TERMS.contains(token)) {
                return RewriteFactValidationResult.fail(
                        RewriteFactViolationCode.NEW_TECHNOLOGY, "改写引入了原文没有的技术名称");
            }
            boolean hasDigit = token.chars().anyMatch(Character::isDigit);
            if (hasDigit) {
                // Vue3、HTTP2 这类“字母+数字”形态一律按技术名处理。
                return RewriteFactValidationResult.fail(
                        RewriteFactViolationCode.NEW_TECHNOLOGY, "改写引入了原文没有的技术名称");
            }
            boolean hasUpperCase = !rawToken.equals(rawToken.toLowerCase(Locale.ROOT));
            if (hasUpperCase) {
                RewriteFactViolationCode code = containsUpperCaseElsewhere(rawToken)
                        ? RewriteFactViolationCode.NEW_TECHNOLOGY
                        : RewriteFactViolationCode.NEW_ENTITY;
                return RewriteFactValidationResult.fail(code, "改写引入了原文没有的技术或实体名称");
            }
        }
        return null;
    }

    private boolean containsUpperCaseElsewhere(String token) {
        for (int index = 1; index < token.length(); index++) {
            if (Character.isUpperCase(token.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    /** 建议文本的责任级别最高层不得超过原文。 */
    private RewriteFactValidationResult checkEscalation(String suggestionCorpus, String originalCorpus) {
        int suggestionTier = responsibilityTier(suggestionCorpus);
        int originalTier = responsibilityTier(originalCorpus);
        if (suggestionTier > originalTier) {
            return RewriteFactValidationResult.fail(
                    RewriteFactViolationCode.RESPONSIBILITY_ESCALATION,
                    "改写升级了原文的责任级别");
        }
        return null;
    }

    private int responsibilityTier(String corpus) {
        if (containsAny(corpus, TIER3_MARKERS) || MANAGE_PEOPLE_PATTERN.matcher(corpus).find()) {
            return 3;
        }
        if (containsAny(corpus, TIER2_MARKERS)) {
            return 2;
        }
        return 0;
    }

    private boolean isPartOfLatinToken(String corpus, int start, int end) {
        boolean latinBefore = start > 0 && isLatinLetter(corpus.charAt(start - 1));
        boolean latinAfter = end < corpus.length() && isLatinLetter(corpus.charAt(end));
        return latinBefore || latinAfter;
    }

    private boolean isLatinLetter(char value) {
        return (value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z');
    }

    private RewriteFactValidationResult checkElementIdentityLeak(String suggestionNfkc) {
        if (ELEMENT_ID_PATTERN.matcher(suggestionNfkc.toLowerCase(Locale.ROOT)).find()) {
            return RewriteFactValidationResult.fail(
                    RewriteFactViolationCode.ELEMENT_IDENTITY_LEAK, "AI 输出中包含疑似结构化元素标识");
        }
        return null;
    }

    private RewriteFactValidationResult checkNewMarkers(
            List<String> markers,
            String suggestionCorpus,
            String originalCorpus,
            RewriteFactViolationCode code,
            String message) {
        for (String marker : markers) {
            String normalizedMarker = stripWhitespace(normalize(marker));
            if (suggestionCorpus.contains(normalizedMarker) && !originalCorpus.contains(normalizedMarker)) {
                return RewriteFactValidationResult.fail(code, message);
            }
        }
        return null;
    }

    private boolean containsAny(String corpus, List<String> markers) {
        for (String marker : markers) {
            if (corpus.contains(stripWhitespace(normalize(marker)))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    private String stripWhitespace(String text) {
        return text.replaceAll("\\s+", "");
    }
}
