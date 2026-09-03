import os
import tempfile
from log_parser.tail_watcher import TailWatcher


def test_tail_watcher_incremental_append():
    """测试 TailWatcher 实时捕获增量追加日志"""
    with tempfile.NamedTemporaryFile(mode="w+", delete=False, suffix=".log", encoding="utf-8") as tmp:
        tmp.write("2026-09-03 12:00:00 [INFO] 127.0.0.1 admin LOGIN SUCCESS - User login\n")
        tmp.flush()
        tmp_path = tmp.name

    try:
        # 初始化监听器（从末尾开始）
        watcher = TailWatcher(tmp_path, start_at_end=True)

        # 初始状态下应该没有任何新行
        records = list(watcher.read_new_records())
        assert len(records) == 0

        # 模拟向日志文件追加两行日志
        with open(tmp_path, "a", encoding="utf-8") as f:
            f.write("2026-09-03 12:01:00 [WARN] 192.168.1.10 root LOGIN FAIL - Password incorrect\n")
            f.write("2026-09-03 12:02:00 [ERROR] 10.0.0.99 guest QUERY FAIL - SQL injection attempt\n")
            f.flush()

        # 再次读取，应精确捕获新追加的两行
        new_records = list(watcher.read_new_records())
        assert len(new_records) == 2
        assert new_records[0]["severity"] == "WARN"
        assert new_records[0]["ip_address"] == "192.168.1.10"
        assert new_records[1]["severity"] == "ERROR"
        assert new_records[1]["ip_address"] == "10.0.0.99"

        # 再次读取，未新增时应返回空列表
        assert len(list(watcher.read_new_records())) == 0

    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)


def test_tail_watcher_read_all():
    """测试 TailWatcher 从头读取现有日志"""
    with tempfile.NamedTemporaryFile(mode="w+", delete=False, suffix=".log", encoding="utf-8") as tmp:
        tmp.write("2026-09-03 10:00:00 [INFO] 183.23.10.1 User user1 LOGOUT SUCCESS - Logout normal\n")
        tmp.flush()
        tmp_path = tmp.name

    try:
        watcher = TailWatcher(tmp_path, start_at_end=False)
        records = list(watcher.read_new_records())
        assert len(records) == 1
        assert records[0]["username"] == "user1"
        assert records[0]["severity"] == "INFO"
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)