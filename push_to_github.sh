#!/bin/bash
# 用法（在你的 Mac 终端里执行，不是在本对话里）：
#   cd /Users/wuyutong/WorkBuddy/2026-10-28-21-12-16/Q5NasBrowser
#   chmod +x push_to_github.sh
#   ./push_to_github.sh https://github.com/你的用户名/Q5NasBrowser.git 你的TOKEN
#
# 说明：
# - 第 1 个参数：GitHub 上【空仓库】的 HTTPS 地址。
# - 第 2 个参数：Personal Access Token（PAT），也可以改成用环境变量 GITHUB_TOKEN 传入。
#   生成 Token：GitHub → Settings → Developer settings → Personal access tokens
#   → Tokens (classic) → Generate new token (classic)
#   → 勾选 repo（全部）和 workflow（必须，仓库里有 .github/workflows 文件）
#   → 生成后【立即复制】，只显示一次。
#
# GitHub 已禁用账号密码登录 Git，密码框里填登录密码一定会失败，必须用上面的 PAT。
# 本脚本把 Token 临时嵌进地址推送，推完立即把地址还原，不会把 Token 留在本地 git 配置里。

set -e
REPO_URL="${1:?用法: $0 <github仓库地址> [token]}"
TOKEN="${2:-$GITHUB_TOKEN}"
if [ -z "$TOKEN" ]; then
  echo "❌ 缺少 Token！请用第 2 个参数传入，或先 export GITHUB_TOKEN=xxx"
  exit 1
fi

cd "$(dirname "$0")"

# 从地址里取出用户名（https://github.com/用户名/仓库.git）
USER=$(echo "$REPO_URL" | sed -E 's#https://github.com/([^/]+)/.*#\1#')
if [ -z "$USER" ]; then
  echo "❌ 无法从地址解析用户名，请确认是 https://github.com/用户名/仓库.git 格式"
  exit 1
fi

# 把 Token 嵌进地址，避免交互输入（macOS 的 osxkeychain 会拒绝密码登录）
AUTH_URL=$(echo "$REPO_URL" | sed -E "s#(https://)(github.com)#\1${USER}:${TOKEN}@\2#")

# 设置 remote
if git remote | grep -qx origin; then
  git remote set-url origin "$AUTH_URL"
else
  git remote add origin "$AUTH_URL"
fi

echo ">> 当前提交:"
git log --oneline -1
echo ">> 强制推送到 origin/main ..."
git push --force -u origin main

# 推完把 remote 还原成不含 Token 的干净地址
git remote set-url origin "$REPO_URL"

echo
echo "✅ 推送成功！现在去 GitHub 仓库的 Actions 页，"
echo "   等待 'Build Q5NasBrowser APK' 跑完，"
echo "   然后在 Artifacts 里下载 q5nasbrowser-debug-apk (内含 app-debug.apk)。"
