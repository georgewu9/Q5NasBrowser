#!/bin/bash
# 用法（在你的 Mac 终端里执行，不是在本对话里）：
#   cd /Users/wuyutong/WorkBuddy/2026-10-28-21-12-16/Q5NasBrowser
#   chmod +x push_to_github.sh
#   ./push_to_github.sh https://github.com/你的用户名/Q5NasBrowser.git
#
# 说明：
# - 第一个参数是你在 GitHub 新建的【空仓库】的地址（HTTPS 或 SSH 都行）。
# - push 时若用 HTTPS，用户名填 GitHub 用户名，密码填【Personal Access Token】(不是账号密码)。
#   生成 Token：GitHub → Settings → Developer settings → Personal access tokens → 勾选 repo 权限。
# - 若用 SSH 地址(git@github.com:...)，需先把你的 SSH 公钥加到 GitHub。

set -e
REPO_URL="${1:?用法: $0 <github仓库地址>}"
cd "$(dirname "$0")"

# 设置 remote（已存在则覆盖）
if git remote | grep -qx origin; then
  git remote set-url origin "$REPO_URL"
else
  git remote add origin "$REPO_URL"
fi

echo ">> 当前提交:"
git log --oneline -1
echo ">> 推送到 origin/main ..."
git push -u origin main

echo
echo "✅ 推送成功！现在去 GitHub 仓库的 Actions 页，"
echo "   等待 'Build Q5NasBrowser APK' 跑完，"
echo "   然后在 Artifacts 里下载 q5nasbrowser-debug-apk (内含 app-debug.apk)。"
