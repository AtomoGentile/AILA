import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from prepare_release import parse_release_tag, configure_ios
from select_sidestore_release import select_release, validate_ios_metadata

class ReleaseTests(unittest.TestCase):
    def release(self, tag, pre=False, draft=False, state='uploaded'):
        return dict(tag_name=tag, prerelease=pre, draft=draft, assets=[dict(name='AILA.ipa', state=state, browser_download_url='https://example.com/AILA.ipa')])

    def test_canonical_tags(self):
        self.assertFalse(parse_release_tag('v1.6.3').beta)
        self.assertTrue(parse_release_tag('v1.6.3-beta.10').beta)
        for invalid in ['v1.6.3-beta.0', 'v1.6.3-beta.01', 'v1.6.3-rc.1', 'v1.6.3-beta', 'main', '1.6.3', 'v1.6.3beta.1']:
            with self.assertRaises(ValueError): parse_release_tag(invalid)

    def test_beta_never_enters_stable_feed_even_if_flag_is_wrong(self):
        self.assertIsNone(select_release(self.release('v1.7.0-beta.1', pre=True)))
        self.assertIsNone(select_release(self.release('v1.7.0-beta.1', pre=False)))
        self.assertIsNone(select_release(self.release('v1.7.0', pre=True)))
        self.assertIsNone(select_release(self.release('v1.7.0', draft=True)))

    def test_only_complete_stable_and_numeric_order(self):
        payload = [self.release('v1.10.0-beta.10', pre=True), self.release('v1.9.0'), self.release('v1.10.0'), self.release('v2.0.0', state='open')]
        self.assertEqual(select_release(payload)['tag_name'], 'v1.10.0')
        self.assertIsNone(select_release(self.release('v1.10.0', state='open')))

    def test_ios_metadata_is_explicit_and_build_increases_for_same_core(self):
        project = '\n'.join(f'        {key}: "old"' for key in ['CFBundleShortVersionString','CFBundleVersion','AILAReleaseVersion','AILAUpdateChannel'])
        beta = configure_ios(project, parse_release_tag('v1.6.3-beta.2'), '166')
        self.assertIn('CFBundleShortVersionString: "1.6.3"', beta)
        self.assertIn('AILAReleaseVersion: "1.6.3-beta.2"', beta)
        self.assertIn('AILAUpdateChannel: "beta"', beta)
        self.assertIn('CFBundleVersion: "166"', beta)
        stable = configure_ios(project, parse_release_tag('v1.6.3'), '167')
        self.assertIn('AILAUpdateChannel: "stable"', stable)
        self.assertIn('CFBundleVersion: "167"', stable)

    def test_old_stable_ipa_supported_but_future_ipa_requires_stable_metadata(self):
        validate_ios_metadata({'CFBundleShortVersionString': '1.6.2'}, 'v1.6.2')
        with self.assertRaises(ValueError):
            validate_ios_metadata({'CFBundleShortVersionString': '1.6.3'}, 'v1.6.3')
        with self.assertRaises(ValueError):
            validate_ios_metadata({'CFBundleShortVersionString': '1.6.3', 'AILAReleaseVersion': '1.6.3-beta.1', 'AILAUpdateChannel': 'beta'}, 'v1.6.3')
        validate_ios_metadata({'CFBundleShortVersionString': '1.6.3', 'AILAReleaseVersion': '1.6.3', 'AILAUpdateChannel': 'stable'}, 'v1.6.3')

if __name__ == '__main__': unittest.main()
