"""
LogScope 实时流式日志文件监听与增量探针 (Tail & Streaming Watcher)
对标 Filebeat / Fluentbit 工业级轻量化采集探针标准：
1. 支持类 tail -f 增量行变动监听；
2. 实时通过 FSM 状态机与正则解析提取结构化元数据；
3. 支持向 AuditVault Webhook 或下游微服务流式实时分发。
"""

import os
import time
from typing import Iterator, Dict, Any, Optional
from log_parser.parser import parse_line


class TailWatcher:
    """实时流式日志文件监听器"""

    def __init__(self, file_path: str, start_at_end: bool = True):
        self.file_path = file_path
        self.start_at_end = start_at_end
        self.last_position = 0
        self._init_position()

    def _init_position(self):
        if os.path.exists(self.file_path):
            with open(self.file_path, "r", encoding="utf-8", errors="replace") as f:
                if self.start_at_end:
                    f.seek(0, os.SEEK_END)
                    self.last_position = f.tell()
                else:
                    self.last_position = 0

    def read_new_records(self) -> Iterator[Dict[str, Any]]:
        """读取当前文件新增追加的所有日志并转换为结构化字典"""
        if not os.path.exists(self.file_path):
            return

        with open(self.file_path, "r", encoding="utf-8", errors="replace") as f:
            f.seek(self.last_position)
            while True:
                line = f.readline()
                if not line:
                    break
                stripped = line.strip()
                if stripped:
                    record = parse_line(stripped)
                    if record:
                        if not record.get("source_file"):
                            record["source_file"] = os.path.basename(self.file_path)
                        yield record
            self.last_position = f.tell()

    def stream_forever(self, poll_interval: float = 0.5, max_iterations: Optional[int] = None) -> Iterator[Dict[str, Any]]:
        """持续阻塞式监听文件追加 (供 CLI 或后台守护进程使用)"""
        count = 0
        while True:
            for record in self.read_new_records():
                yield record
            if max_iterations is not None:
                count += 1
                if count >= max_iterations:
                    break
            time.sleep(poll_interval)