#!/usr/bin/env python3
"""Hermetic checks for the live lane's opt-in and checkout-local locking."""
import fcntl
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


class LiveLaneGuardTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        tools = self.root / "tools"
        tools.mkdir()
        self.script = tools / "test-trino-introspection-live.sh"
        shutil.copy2(Path(__file__).with_name(self.script.name), self.script)
        self.commands = self.root / "docker-commands.txt"
        docker = tools / "docker"
        docker.write_text('''#!/usr/bin/env bash
printf '%s\\n' "$*" >> "$DOCKER_COMMANDS"
if [[ $1 == ps ]]; then
    echo 'clickhouse/clickhouse-server:26.10 unrelated-clickhouse'
elif [[ $1 == network && $2 == create ]]; then
    exit 99 # Stop before any container or Gradle process could start.
fi
''')
        docker.chmod(0o755)
        self.env = dict(os.environ, PATH=f"{tools}:{os.environ['PATH']}",
                        DOCKER_COMMANDS=str(self.commands), TRINO_LIVE="1")

    def run_lane(self):
        return subprocess.run(["bash", str(self.script)], env=self.env,
                              capture_output=True, text=True, timeout=10)

    def test_requires_explicit_opt_in(self):
        self.env.pop("TRINO_LIVE")
        result = self.run_lane()
        self.assertEqual(2, result.returncode, result.stderr)
        self.assertFalse(self.commands.exists())

    def test_unrelated_containers_do_not_block_the_lane(self):
        result = self.run_lane()
        self.assertEqual(99, result.returncode, result.stderr)
        commands = self.commands.read_text().splitlines()
        self.assertTrue(any(command.startswith("network create ") for command in commands))
        self.assertFalse(any(command.split()[0] == "ps" for command in commands))

    def test_same_checkout_cannot_run_two_live_lanes(self):
        scratch = self.root / "build/introspection-parity/live"
        scratch.mkdir(parents=True)
        with (scratch / "lane.lock").open("w") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            result = self.run_lane()
        self.assertEqual(75, result.returncode, result.stderr)
        self.assertIn("This live lane is already running", result.stderr)
        self.assertFalse(self.commands.exists())


if __name__ == "__main__":
    unittest.main()
