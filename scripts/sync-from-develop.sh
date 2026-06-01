#!/usr/bin/env bash
# Merges develop into igly/cortex and restores private CORTEX commands
# if they are lost during conflict resolution.
#
# Usage: ./scripts/sync-from-develop.sh
set -euo pipefail

BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ "$BRANCH" != "igly/cortex" ]; then
  echo "ERROR: must be on igly/cortex (currently on $BRANCH)" >&2
  exit 1
fi

echo "Merging develop into igly/cortex..."
git merge develop --no-edit || true

# Resolve known conflicts by keeping igly/cortex version (ours)
CONFLICTS=$(git diff --name-only --diff-filter=U 2>/dev/null || true)
if [ -n "$CONFLICTS" ]; then
  echo "Resolving conflicts (keeping igly/cortex additions)..."
  for f in $CONFLICTS; do
    git checkout --ours "$f"
    git add "$f"
  done
fi

# Ensure private commands are registered in CommandManager.kt
CM="src/main/kotlin/com/koupper/cli/CommandManager.kt"
if ! grep -q "StartCommand()" "$CM"; then
  echo "Restoring StartCommand + MonitorCommand in CommandManager..."
  sed -i 's/RECONCILE to ReconcileCommand(),/RECONCILE to ReconcileCommand(),\n            START    to StartCommand(),\n            MONITOR  to MonitorCommand(),/' "$CM"
fi

# Ensure private constants exist in AvailableCommands.kt
AC="src/main/kotlin/com/koupper/cli/commands/AvailableCommands.kt"
if ! grep -q 'const val START' "$AC"; then
  echo "Restoring START + MONITOR constants in AvailableCommands..."
  sed -i 's/const val RECONCILE = "reconcile"/const val RECONCILE = "reconcile"\n    const val START    = "start"\n    const val MONITOR  = "monitor"/' "$AC"
fi
if ! grep -q '"Start the full CORTEX' "$AC"; then
  sed -i 's/RECONCILE to "Orchestrates/START    to "Start the full CORTEX stack: worker + web UI + monitor in one command",\n        MONITOR  to "Launches the IGLY CORTEX real-time swarm dashboard",\n        RECONCILE to "Orchestrates/' "$AC"
fi

# Ensure imports exist in CommandManager.kt
if ! grep -q 'AvailableCommands.START' "$CM"; then
  sed -i '/import com.koupper.cli.commands.AvailableCommands.RECONCILE/a import com.koupper.cli.commands.AvailableCommands.START\nimport com.koupper.cli.commands.AvailableCommands.MONITOR' "$CM"
fi

if [ -n "$(git diff --cached --name-only)" ]; then
  git add "$CM" "$AC"
  git commit -m "chore(igly/cortex): merge develop + restore CORTEX commands"
  echo "Done. Merge complete."
else
  echo "Done. No extra fixes needed."
fi
