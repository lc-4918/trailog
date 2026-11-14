"""Tests des fonctions pures de build_index.py : python3 -m unittest test_build_index (sans osmium)."""
import sys
import types
import unittest

# build_index importe osmium au chargement ; ces tests n'en ont pas besoin.
sys.modules.setdefault("osmium", types.ModuleType("osmium"))
import build_index  # noqa: E402


class MergeIndexLinesTest(unittest.TestCase):
    def test_regenerated_tile_replaces_old_line_and_others_are_kept(self):
        existing = ["E0_N45 10 20 1\n", "E5_N45 30 40 1\n"]
        merged = build_index.merge_index_lines(existing, ["E5_N45 31 41 2"])
        self.assertEqual(["E0_N45 10 20 1", "E5_N45 31 41 2"], merged)

    def test_lines_are_sorted_by_tile_name_and_blank_lines_dropped(self):
        merged = build_index.merge_index_lines(["\n"], ["W5_N40 1 2 3", "E0_N40 4 5 6"])
        self.assertEqual(["E0_N40 4 5 6", "W5_N40 1 2 3"], merged)


class SelectTilesTest(unittest.TestCase):
    def test_without_names_every_tile_is_written(self):
        keys = [(5, 45), (0, 45)]
        self.assertEqual([(0, 45), (5, 45)], build_index.select_tiles(keys, set()))

    def test_margin_neighbours_are_left_out(self):
        keys = [(5, 45), (0, 45), (10, 45), (5, 40)]
        self.assertEqual([(5, 45)], build_index.select_tiles(keys, {"E5_N45"}))

    def test_west_and_south_tiles_are_matched_by_name(self):
        keys = [(-10, 35), (-5, 35)]
        self.assertEqual([(-10, 35)], build_index.select_tiles(keys, {"W10_N35"}))


class TileNameTest(unittest.TestCase):
    def test_tile_of_point_west_of_greenwich(self):
        self.assertEqual("W5_N45", build_index.tile_name(*build_index.tile_of(45.2, -1.5)))


if __name__ == "__main__":
    unittest.main()
