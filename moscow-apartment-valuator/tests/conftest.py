import sys
from pathlib import Path

# Make the `mav` package importable when tests are run without installing it.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
