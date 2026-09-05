import base64
import unittest

from collect_transaction_corpus import base58, compact, elements, members, wire_signature


class CollectorTest(unittest.TestCase):
    def test_raw_json_preserves_large_numbers_exponents_and_string_whitespace(self):
        raw = ' { "integer":18446744073709551615, "decimal":1.234567890123456789e-20, "text":" spaces \\" ", "nested": [ null, {"flag":true} ] } '
        compacted = compact(raw)
        fields = members(compacted)
        self.assertEqual(fields['integer'], '18446744073709551615')
        self.assertEqual(fields['decimal'], '1.234567890123456789e-20')
        self.assertEqual(fields['text'], '" spaces \\" "')
        self.assertEqual(list(elements(fields['nested'])), ['null', '{"flag":true}'])

    def test_leading_zero_bytes_in_base58(self):
        self.assertEqual(base58(bytes(32)), '1' * 32)
        self.assertEqual(base58(bytes([0, 1])), '12')

    def test_signature_position_depends_on_envelope_version(self):
        first, second = bytes(range(64)), bytes(reversed(range(64)))
        legacy = bytes([2]) + first + second + bytes([2, 0, 0])
        v0 = bytes([2]) + first + second + bytes([128, 2, 0, 0])
        v1 = bytes([129, 2, 0, 0]) + bytes(200) + first + second
        for wire in [legacy, v0, v1]:
            self.assertEqual(wire_signature(base64.b64encode(wire)), base58(first))


if __name__ == '__main__':
    unittest.main()
