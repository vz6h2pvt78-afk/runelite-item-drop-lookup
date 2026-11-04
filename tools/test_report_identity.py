import json
from pathlib import Path
import tempfile
import unittest
from check_report_identity import METADATA, RESOURCES, REQUIRED, check, dataset_digest


class ReportIdentityTest(unittest.TestCase):
    def test_checked_in_metadata_accounts_for_all_runtime_json(self):
        self.assertEqual(dataset_digest(), check()["datasetSha256"])
        self.assertTrue(REQUIRED <= {p.name for p in RESOURCES.glob("*.json")})

    def test_changed_data_and_wrong_checkpoint_label_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "Checkpoint/tag"):
            check(build="not-the-candidate")
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in REQUIRED:
                (root / name).write_text('{"schemaVersion":1}', encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "does not match"):
                check(METADATA, root)
            before = dataset_digest(root)
            (root / "item_sources.json").write_text('{"different":true}', encoding="utf-8")
            self.assertNotEqual(before, dataset_digest(root))
            (root / "item_sources.json").unlink()
            with self.assertRaisesRegex(ValueError, "datasets missing"):
                dataset_digest(root)
