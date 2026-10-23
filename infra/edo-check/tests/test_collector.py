from test_relay import load
import unittest

collector = load('collector')


class MetricTests(unittest.TestCase):
    def test_memory_subtracts_inactive_file_cache_without_negative_values(self):
        self.assertEqual(collector.resource_stats({'memory_stats': {'usage': 100, 'stats': {'inactive_file': 20}}})['memory'], 80)
        self.assertEqual(collector.resource_stats({'memory_stats': {'usage': 10, 'stats': {'cache': 100}}})['memory'], 0)

    def test_cpu_accounts_for_online_cores(self):
        stats = {'cpu_stats': {'cpu_usage': {'total_usage': 300}, 'system_cpu_usage': 1000, 'online_cpus': 4},
                 'precpu_stats': {'cpu_usage': {'total_usage': 100}, 'system_cpu_usage': 600}}
        self.assertEqual(collector.resource_stats(stats)['cpu'], 200)

    def test_shared_image_storage_is_not_sum_of_virtual_image_sizes(self):
        self.assertEqual(collector.image_disk_bytes({'LayersSize': 1000, 'Images': [{'Size': 600}, {'Size': 700}]}), 1000)

    def test_missing_stats_are_not_reported_as_zero_usage(self):
        metrics = collector.resource_stats({})
        self.assertFalse(metrics['memory_available'])
        self.assertFalse(metrics['cpu_available'])


if __name__ == '__main__':
    unittest.main()
