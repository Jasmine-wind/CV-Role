# Parse Regression Report

- Generated at: 2026-05-18 12:46:29
- Expected samples: 15
- Evaluated outputs: 15
- Passed outputs: 0/15
- basicInfoAccuracy: 213/252 (84.5%)
- sectionAccuracy: 728/792 (91.9%)

## Summary

| sampleId | mode | status | basicInfo | sections | pureIndexLineCount | othersCount | duplicateWarningCount | totalParseDurationMs | aiDurationMs | cacheHit | fallbackOccurred |
| --- | --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | --- |
| sample-001-java-intern-docx | ACCURATE | FAIL | 7/9 (77.8%) | 12/16 (75.0%) | 0 | 1 | 1 | 179425 | 179141 | false | false |
| sample-001-java-intern-docx | BALANCED | FAIL | 7/9 (77.8%) | 6/16 (37.5%) | 0 | 5 | 0 | 137783 | 137730 | false | true |
| sample-001-java-intern-docx | FAST | FAIL | 7/9 (77.8%) | 6/16 (37.5%) | 0 | 5 | 0 | 39 | 0 | false | true |
| sample-002-javaee-engineer-2y-docx | ACCURATE | FAIL | 15/19 (78.9%) | 53/57 (93.0%) | 0 | 0 | 1 | 130478 | 130373 | false | false |
| sample-002-javaee-engineer-2y-docx | BALANCED | FAIL | 15/19 (78.9%) | 53/57 (93.0%) | 1 | 0 | 1 | 59126 | 58992 | false | true |
| sample-002-javaee-engineer-2y-docx | FAST | FAIL | 15/19 (78.9%) | 53/57 (93.0%) | 1 | 1 | 1 | 163 | 0 | false | true |
| sample-003-javaee-engineer-2y-pdf | ACCURATE | FAIL | 17/19 (89.5%) | 42/48 (87.5%) | 0 | 2 | 0 | 115379 | 115151 | false | false |
| sample-003-javaee-engineer-2y-pdf | BALANCED | FAIL | 15/19 (78.9%) | 41/48 (85.4%) | 0 | 0 | 0 | 37664 | 37590 | false | true |
| sample-003-javaee-engineer-2y-pdf | FAST | FAIL | 15/19 (78.9%) | 40/48 (83.3%) | 0 | 2 | 0 | 61 | 0 | false | true |
| sample-004-javaee-developer-2y-docx | ACCURATE | FAIL | 13/17 (76.5%) | 67/69 (97.1%) | 0 | 0 | 0 | 123231 | 123147 | false | false |
| sample-004-javaee-developer-2y-docx | BALANCED | FAIL | 15/17 (88.2%) | 67/69 (97.1%) | 0 | 2 | 0 | 130191 | 130123 | false | false |
| sample-004-javaee-developer-2y-docx | FAST | FAIL | 15/17 (88.2%) | 66/69 (95.7%) | 0 | 4 | 0 | 348 | 0 | false | true |
| sample-005-javaee-engineer-2y-pdf | ACCURATE | FAIL | 19/20 (95.0%) | 74/74 (100.0%) | 0 | 0 | 0 | 43 | 0 | false | true |
| sample-005-javaee-engineer-2y-pdf | BALANCED | FAIL | 19/20 (95.0%) | 74/74 (100.0%) | 0 | 0 | 0 | 43 | 0 | false | true |
| sample-005-javaee-engineer-2y-pdf | FAST | FAIL | 19/20 (95.0%) | 74/74 (100.0%) | 0 | 0 | 0 | 51 | 0 | false | true |

## Details

### sample-001-java-intern-docx / ACCURATE
- totalParseDurationMs 179425 > 60000
- basicInfo.name: expected value `四叶草`, actual `组织`
- basicInfo.resumeType: expected value `INTERN`, actual `STUDENT`
- sections.education: missing `重庆理工大学`
- sections.education: missing `JAVA 开发`
- sections.skills: missing `Nginx`
- sections.campusExperiences: missing `在校经历`

### sample-001-java-intern-docx / BALANCED
- totalParseDurationMs 137783 > 60000
- basicInfo.name: expected value `四叶草`, actual `组织`
- basicInfo.resumeType: expected value `INTERN`, actual `STUDENT`
- sections.education: missing `重庆理工大学`
- sections.education: missing `JAVA 开发`
- sections.skills: missing `Java`
- sections.skills: missing `Vue`
- sections.skills: missing `JavaScript`
- sections.skills: missing `MySQL`
- sections.skills: missing `MyBatis`
- sections.skills: missing `Nginx`
- sections.campusExperiences: missing `在校经历`
- sections.others: forbidden text `2、` appeared

### sample-001-java-intern-docx / FAST
- basicInfo.name: expected value `四叶草`, actual `组织`
- basicInfo.resumeType: expected value `INTERN`, actual `STUDENT`
- sections.education: missing `重庆理工大学`
- sections.education: missing `JAVA 开发`
- sections.skills: missing `Java`
- sections.skills: missing `Vue`
- sections.skills: missing `JavaScript`
- sections.skills: missing `MySQL`
- sections.skills: missing `MyBatis`
- sections.skills: missing `Nginx`
- sections.campusExperiences: missing `在校经历`
- sections.others: forbidden text `2、` appeared

### sample-002-javaee-engineer-2y-docx / ACCURATE
- totalParseDurationMs 130478 > 60000
- basicInfo.name: expected value `杨玉环`, actual `基本情况`
- basicInfo.name: forbidden value `基本情况` appeared
- basicInfo.school: expected one of ['河南工学院'], actual `<empty>`
- basicInfo.workYears: expected one of ['2年'], actual `29年`
- sections.education: missing `2016.9`
- sections.education: missing `2019.6`
- sections.workExperiences: missing `JavaEE软件工程师`
- sections.others: forbidden text `个人简历` appeared

### sample-002-javaee-engineer-2y-docx / BALANCED
- pureIndexLineCount 1 > 0
- basicInfo.name: expected value `杨玉环`, actual `基本情况`
- basicInfo.name: forbidden value `基本情况` appeared
- basicInfo.school: expected one of ['河南工学院'], actual `<empty>`
- basicInfo.workYears: expected one of ['2年'], actual `29年`
- sections.education: missing `2016.9`
- sections.education: missing `2019.6`
- sections.workExperiences: missing `JavaEE软件工程师`
- sections.others: forbidden text `个人简历` appeared

### sample-002-javaee-engineer-2y-docx / FAST
- pureIndexLineCount 1 > 0
- basicInfo.name: expected value `杨玉环`, actual `基本情况`
- basicInfo.name: forbidden value `基本情况` appeared
- basicInfo.school: expected one of ['河南工学院'], actual `<empty>`
- basicInfo.workYears: expected one of ['2年'], actual `29年`
- sections.education: missing `2016.9`
- sections.education: missing `2019.6`
- sections.workExperiences: missing `JavaEE软件工程师`
- sections.others: forbidden text `个人简历` appeared

### sample-003-javaee-engineer-2y-pdf / ACCURATE
- totalParseDurationMs 115379 > 60000
- basicInfo.school: expected one of ['河南经贸职业学院'], actual `<empty>`
- basicInfo.workYears: expected one of ['2'], actual `17年`
- sections.skills: missing `SpringBoot`
- sections.skills: missing `Dubbo`
- sections.skills: missing `Zookeeper`
- sections.skills: missing `Redis`
- sections.skills: missing `SpringCloud`
- sections.skills: missing `IntelliJ IDEA`

### sample-003-javaee-engineer-2y-pdf / BALANCED
- basicInfo.name: expected value `段焯峰`, actual `<empty>`
- basicInfo.school: expected one of ['河南经贸职业学院'], actual `<empty>`
- basicInfo.jobIntention: expected one of ['Java 后台开发', 'Java后台开发', 'Java 开发工程师'], actual `<empty>`
- basicInfo.workYears: expected one of ['2'], actual `17年`
- sections.skills: missing `SpringBoot`
- sections.skills: missing `Dubbo`
- sections.skills: missing `Zookeeper`
- sections.skills: missing `Redis`
- sections.skills: missing `SpringCloud`
- sections.skills: missing `IntelliJ IDEA`
- sections.others: forbidden text `个人简历` appeared

### sample-003-javaee-engineer-2y-pdf / FAST
- basicInfo.name: expected value `段焯峰`, actual `<empty>`
- basicInfo.school: expected one of ['河南经贸职业学院'], actual `<empty>`
- basicInfo.jobIntention: expected one of ['Java 后台开发', 'Java后台开发', 'Java 开发工程师'], actual `<empty>`
- basicInfo.workYears: expected one of ['2'], actual `17年`
- sections.skills: missing `SpringBoot`
- sections.skills: missing `Dubbo`
- sections.skills: missing `Zookeeper`
- sections.skills: missing `Redis`
- sections.skills: missing `SpringCloud`
- sections.skills: missing `IntelliJ IDEA`
- sections.others: forbidden text `个人简历` appeared
- sections.others: forbidden text `姓 名 段焯峰` appeared

### sample-004-javaee-developer-2y-docx / ACCURATE
- totalParseDurationMs 123231 > 60000
- basicInfo.name: expected value `西施`, actual `基本资料`
- basicInfo.name: forbidden value `基本资料` appeared
- basicInfo.school: expected one of ['郑州轻工业学院'], actual `<empty>`
- basicInfo.jobIntention: expected one of ['JAVA软件工程师', 'Java软件工程师'], actual `全职，目标地点：郑州`
- sections.skills: missing `Eureka`
- sections.skills: missing `Nginx`

### sample-004-javaee-developer-2y-docx / BALANCED
- totalParseDurationMs 130191 > 60000
- basicInfo.name: expected value `西施`, actual `<empty>`
- basicInfo.jobIntention: expected one of ['JAVA软件工程师', 'Java软件工程师'], actual `<empty>`
- sections.skills: missing `Eureka`
- sections.skills: missing `Nginx`

### sample-004-javaee-developer-2y-docx / FAST
- basicInfo.name: expected value `西施`, actual `<empty>`
- basicInfo.jobIntention: expected one of ['JAVA软件工程师', 'Java软件工程师'], actual `<empty>`
- sections.skills: missing `Eureka`
- sections.skills: missing `Nginx`
- sections.others: forbidden text `6.` appeared

### sample-005-javaee-engineer-2y-pdf / ACCURATE
- basicInfo.school: expected one of ['黄淮学院'], actual `<empty>`

### sample-005-javaee-engineer-2y-pdf / BALANCED
- basicInfo.school: expected one of ['黄淮学院'], actual `<empty>`

### sample-005-javaee-engineer-2y-pdf / FAST
- basicInfo.school: expected one of ['黄淮学院'], actual `<empty>`

