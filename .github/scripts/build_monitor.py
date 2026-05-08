#!/usr/bin/env python3
"""
云端构建状态监控工具
用于监控 GitHub Actions 构建状态并发送通知
"""

import requests
import json
from datetime import datetime
from typing import Dict, List, Optional

class CloudBuildMonitor:
    """GitHub Actions 构建监控器"""

    def __init__(self, token: str, owner: str, repo: str):
        self.token = token
        self.owner = owner
        self.repo = repo
        self.headers = {
            "Authorization": f"token {token}",
            "Accept": "application/vnd.github.v3+json"
        }

    def get_workflow_runs(self, workflow_name: Optional[str] = None) -> List[Dict]:
        """获取工作流运行列表"""
        url = f"https://api.github.com/repos/{self.owner}/{self.repo}/actions/runs"
        if workflow_name:
            url += f"?workflow_filename={workflow_name}"
        response = requests.get(url, headers=self.headers)
        return response.json().get('workflow_runs', [])

    def get_workflow_run_status(self, run_id: int) -> Dict:
        """获取特定工作流运行状态"""
        url = f"https://api.github.com/repos/{self.owner}/{self.repo}/actions/runs/{run_id}"
        response = requests.get(url, headers=self.headers)
        return response.json()

    def monitor_build(self, workflow_name: str = "cloud_build.yml", poll_interval: int = 30):
        """监控构建状态"""
        print(f"🔍 开始监控工作流: {workflow_name}")
        print("=" * 50)

        while True:
            runs = self.get_workflow_runs(workflow_name)

            if not runs:
                print("暂无运行中的构建")
                break

            latest_run = runs[0]
            status = latest_run['status']
            conclusion = latest_run['conclusion']
            created_at = latest_run['created_at']

            print(f"\n📋 最新构建:")
            print(f"  状态: {status}")
            print(f"  结果: {conclusion or '进行中'}")
            print(f"  时间: {created_at}")
            print(f"  链接: {latest_run['html_url']}")

            if status == 'completed':
                if conclusion == 'success':
                    print("\n✅ 构建成功完成!")
                else:
                    print(f"\n❌ 构建失败: {conclusion}")
                break

            print(f"\n⏳ 等待 {poll_interval} 秒后刷新...")
            import time
            time.sleep(poll_interval)

def main():
    """主函数"""
    print("🔧 云端构建监控工具")
    print("=" * 50)

    # 配置
    import os
    token = os.getenv('GITHUB_TOKEN')
    owner = "gedoor"
    repo = "legado"

    if not token:
        print("❌ 请设置 GITHUB_TOKEN 环境变量")
        print("export GITHUB_TOKEN=your_token_here")
        return

    monitor = CloudBuildMonitor(token, owner, repo)
    monitor.monitor_build()

if __name__ == "__main__":
    main()
