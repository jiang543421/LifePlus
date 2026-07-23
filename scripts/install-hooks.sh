#!/usr/bin/env bash
# LifePulse git hooks 安装脚本（v2.1 PR4）。
#
# 用法：在仓库根目录执行 ./scripts/install-hooks.sh
# 效果：git config core.hooksPath .githooks，从此本仓库的 pre-commit 走
#       .githooks/pre-commit（即 gitleaks secret scan）。
#
# 回滚：git config --unset core.hooksPath 即可还原为 .git/hooks/。
#
# 注意：该设置是仓库本地的（写入 .git/config，不入提交），每个开发者克隆后
# 各跑一次即可。CI 端必须在工作流里同样配置或显式调 gitleaks。

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HOOKS_DIR="$REPO_ROOT/.githooks"

if [ ! -d "$HOOKS_DIR" ]; then
  echo "[install-hooks] .githooks/ not found at $HOOKS_DIR" >&2
  exit 1
fi

# 给 pre-commit 加上执行权限（Windows checkout 经常丢 +x）
chmod +x "$HOOKS_DIR/pre-commit"

# 关键步骤：把 hooks 路径从 .git/hooks 切到 .githooks
git -C "$REPO_ROOT" config core.hooksPath .githooks

echo "[install-hooks] OK → core.hooksPath = .githooks"
echo "[install-hooks] Verify: git config --get core.hooksPath"
