import os
import re

POSTS_DIR = r"E:\java-portfolio\02-tech-blog\source\_posts"
OUTPUT_DIR = r"E:\java-portfolio\social-publish\ready-to-post"
os.makedirs(OUTPUT_DIR, exist_ok=True)

# 平台模板配置
PLATFORMS = {
    "juejin": {
        "name": "稀土掘金",
        "header_tpl": """# {title}

> **作者**：郑锦城 (Emiliamio)  
> **项目主页**：[GitHub - AgentForge](https://github.com/Emiliamio/agent-forge) ｜ [GitHub - Java-Portfolio](https://github.com/Emiliamio/java-portfolio) ｜ [个人独立博客](https://emiliamio.github.io)  
> **推荐标签**：后端, Java, Spring Boot, 人工智能, 架构设计  

---
""",
        "footer_tpl": """
---

## 👨‍💻 作者与开源交流

本项目源码已全面开源并配套全栈自动化测试：
* 👑 **AgentForge 旗舰中台源码**：[https://github.com/Emiliamio/agent-forge](https://github.com/Emiliamio/agent-forge)（欢迎 Star ⭐️）
* 🛡️ **AuditVault 审计中台源码**：[https://github.com/Emiliamio/java-portfolio](https://github.com/Emiliamio/java-portfolio)
* 🌐 **在线独立博客**：[https://emiliamio.github.io](https://emiliamio.github.io)
* 📬 **技术交流邮箱**：`mio2110767128@163.com`

*如果本文对您有启发，欢迎在 GitHub 点个 Star 或在评论区留言讨论！*
"""
    },
    "csdn": {
        "name": "CSDN",
        "header_tpl": """# {title}

**版权声明**：本文为博主「郑锦城 (Emiliamio)」的原创文章，遵循 CC 4.0 BY-SA 版权协议。  
**源码地址**：[https://github.com/Emiliamio/agent-forge](https://github.com/Emiliamio/agent-forge) 与 [https://github.com/Emiliamio/java-portfolio](https://github.com/Emiliamio/java-portfolio)  
**分类专栏**：企业级架构设计 / Java 21 / AI Agent 与大模型 / 高并发性能调优  

---
""",
        "footer_tpl": """
---

## 🎯 总结与项目源码获取

全套系统工程已实现 **211 项自动化测试 100% 真实绿灯通过**，拒绝任何虚假假功能：
* **AgentForge 核心仓库**：[https://github.com/Emiliamio/agent-forge](https://github.com/Emiliamio/agent-forge)
* **AuditVault 审计仓库**：[https://github.com/Emiliamio/java-portfolio](https://github.com/Emiliamio/java-portfolio)
* **在线博客展厅**：[https://emiliamio.github.io](https://emiliamio.github.io)
* **联系作者**：`mio2110767128@163.com`

**欢迎大家在 GitHub 点亮 Star ⭐️ 关注！**
"""
    },
    "zhihu": {
        "name": "知乎",
        "header_tpl": """# {title}

> 本文探讨企业级 AI Agent 与后端工程化落地。所有架构方案均已在真实生产环境与信创测试中通过严苛压测。  
> 完整开源工程见 GitHub：`github.com/Emiliamio/agent-forge` 及 `github.com/Emiliamio/java-portfolio`。

---
""",
        "footer_tpl": """
---

## 写在最后

在当下的技术环境中，把一个技术方案从“能跑通的玩具 Demo”打磨到“具备严格租户隔离、防爆 OOM、长尾容错与密码学防伪的工业级中台”，往往需要付出 10 倍于写核心逻辑的精力。

* 完整源码与 211 项单测：**GitHub 搜索 `Emiliamio/agent-forge` 与 `Emiliamio/java-portfolio`**
* 独立技术博客：**`https://emiliamio.github.io`**
* 如有技术探讨或企业私有化交付需求，欢迎私信或邮件联系：`mio2110767128@163.com`。
"""
    }
}

def parse_post(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    # 解析 frontmatter
    fm_match = re.match(r"^---\n(.*?)\n---\n(.*)$", content, re.DOTALL)
    if not fm_match:
        return {"title": os.path.basename(file_path), "body": content}

    fm_text = fm_match.group(1)
    body = fm_match.group(2)

    title_match = re.search(r"^title:\s*(.*)$", fm_text, re.MULTILINE)
    title = title_match.group(1).strip("'\" ") if title_match else os.path.basename(file_path)

    # 替换站内相对链接为博客绝对链接
    body = re.sub(r'\]\(/(\d{4}/\d{2}/\d{2}/[^)]+)\)', r'](https://emiliamio.github.io/\1)', body)

    return {"title": title, "body": body}

# 核心重点发布的精选文章列表与爆款标题适配
FEATURED_POSTS = [
    {
        "file": "agentforge-pure-java-enterprise-rag-architecture.md",
        "slug": "01_纯血Java21_AIAgent中台架构",
        "custom_titles": {
            "juejin": "打破 Python 垄断！纯血 Java 21 + Spring Boot 3.2 企业级 AI Agent 与三路混合 RAG 中台全栈架构实战",
            "csdn": "纯血 Java 21 企业级 AI Agent 架构实践：为什么我们用 Spring Boot 3.2 替代 Python 生态？",
            "zhihu": "为什么在大模型商业落地中，我们最终选择用纯血 Java 21 替代 Python 生态？"
        }
    },
    {
        "file": "poi-sxssf-hyperloglog-high-concurrency.md",
        "slug": "02_海量日志导出防OOM与亿级基数统计",
        "custom_titles": {
            "juejin": "海量数据导出如何防 JVM OOM？SXSSFWorkbook 流式写入与 Redis HyperLogLog 亿级基数统计实战",
            "csdn": "【生产踩坑复盘】5万行报表导出如何把内存压在 18MB？SXSSF 磁盘滑动窗口与 HyperLogLog 实战",
            "zhihu": "高并发下如何优雅避免 Excel 导出引起的 JVM FullGC 与 OOM？"
        }
    },
    {
        "file": "auditvault-spring-boot-architecture.md",
        "slug": "03_高并发分布式日志审计系统实战",
        "custom_titles": {
            "juejin": "从零构建 Datadog 级日志审计与可观测性中枢：Spring Boot 3 + Kafka + ClickHouse 工业全栈实践",
            "csdn": "企业级高并发分布式日志审计平台实践：Spring Boot 3 + Redis 7 + MySQL + ClickHouse 深度剖析",
            "zhihu": "如何从零搭建一套工业级高并发日志审计与 SOC 安全遥测中台？"
        }
    },
    {
        "file": "python-log-parser-anomaly-detection.md",
        "slug": "04_3万QPS日志状态机探针实录",
        "custom_titles": {
            "juejin": "3.4 万 QPS！基于 mmap 零拷贝与 FSM 有限状态机的极速日志异常探针研发实录",
            "csdn": "Python 多核 mmap 零拷贝与状态机解析：34,317 QPS 日志清洗与暴力破解检测实战",
            "zhihu": "单机 3.4 万行/秒，如何用 Python + 有限状态机高效解析海量日志？"
        }
    },
    {
        "file": "jwt-redis-blacklist-security.md",
        "slug": "05_无状态JWT即时吊销与防爆破实战",
        "custom_titles": {
            "juejin": "无状态 JWT 如何做到即时吊销与防暴力破解？基于 Redis 黑名单与滑动窗口限流的金融级安全实战",
            "csdn": "Spring Security 6 + Redis 7：无状态 JWT 优雅吊销与防爆破实战方案",
            "zhihu": "无状态的 JWT Token，到底该如何优雅实现主动注销与黑名单？"
        }
    }
]

generated_files = []

for item in FEATURED_POSTS:
    file_path = os.path.join(POSTS_DIR, item["file"])
    if not os.path.exists(file_path):
        print(f"Skipping {file_path}, not found.")
        continue

    parsed = parse_post(file_path)

    for plat_key, plat_info in PLATFORMS.items():
        title = item["custom_titles"].get(plat_key, parsed["title"])
        header = plat_info["header_tpl"].format(title=title)
        footer = plat_info["footer_tpl"]

        full_content = header + parsed["body"] + footer

        out_filename = f"{item['slug']}_{plat_info['name']}.md"
        out_path = os.path.join(OUTPUT_DIR, out_filename)

        with open(out_path, "w", encoding="utf-8") as f:
            f.write(full_content)

        generated_files.append((plat_info['name'], out_filename))

print(f"Successfully generated {len(generated_files)} platform-ready markdown files in {OUTPUT_DIR}")
for plat, fname in generated_files:
    print(f"  [{plat}] {fname}")