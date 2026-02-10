// Ported from https://github.com/adraffy/ens-normalize.java (MIT License)
// Copyright 2023 Andrew Raffensperger
// Permission is hereby granted, free of charge, to any person obtaining a copy of this
// software and associated documentation files (the "Software"), to deal in the Software
// without restriction, including without limitation the rights to use, copy, modify, merge,
// publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons
// to whom the Software is furnished to do so, subject to the following conditions:
// The above copyright notice and this permission notice shall be included in all copies or
// substantial portions of the Software.
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED.

package io.ethers.ens.normalize

internal class ENSIP15(val nf: NF, dec: Decoder) {
    private val maxNonSpacingMarks: Int
    private val shouldEscape: ReadOnlyIntSet
    private val ignored: ReadOnlyIntSet
    private val combiningMarks: ReadOnlyIntSet
    private val nonSpacingMarks: ReadOnlyIntSet
    private val nfcCheck: ReadOnlyIntSet
    private val possiblyValid: ReadOnlyIntSet
    private val fenced: Map<Int, String>
    private val mapped: Map<Int, ReadOnlyIntList>
    private val groups: List<Group>
    private val emojis: List<EmojiSequence>
    private val wholes: List<Whole>

    private val confusables = HashMap<Int, Whole>()
    private val emojiRoot = EmojiNode()
    private val LATIN: Group
    private val GREEK: Group
    private val ASCII: Group
    private val EMOJI: Group

    init {
        shouldEscape = ReadOnlyIntSet.fromOwnedUnsorted(dec.readUnique())
        ignored = ReadOnlyIntSet.fromOwnedUnsorted(dec.readUnique())
        combiningMarks = ReadOnlyIntSet.fromOwnedUnsorted(dec.readUnique())
        maxNonSpacingMarks = dec.readUnsigned()
        nonSpacingMarks = ReadOnlyIntSet.fromOwnedUnsorted(dec.readUnique())
        nfcCheck = ReadOnlyIntSet.fromOwnedUnsorted(dec.readUnique())
        fenced = decodeNamedCodepoints(dec)
        mapped = decodeMapped(dec)
        groups = decodeGroups(dec)
        emojis = dec.readTree { cps -> EmojiSequence(cps) }

        // precompute: confusable-extent complements
        wholes = decodeWholes(dec)

        // precompute: emoji trie
        for (emoji in emojis) {
            val nodes = ArrayList<EmojiNode>()
            nodes.add(emojiRoot)
            for (cp in emoji.beautified.array) {
                if (cp == 0xFE0F) {
                    val size = nodes.size
                    for (i in 0 until size) {
                        nodes.add(nodes[i].then(cp))
                    }
                } else {
                    for (i in nodes.indices) {
                        nodes[i] = nodes[i].then(cp)
                    }
                }
            }
            for (x in nodes) {
                x.emoji = emoji
            }
        }

        // precompute: possibly valid
        val union = HashSet<Int>()
        val multi = HashSet<Int>()
        for (g in groups) {
            for (cp in g.primary.array) {
                if (!union.add(cp)) multi.add(cp)
            }
            for (cp in g.secondary.array) {
                if (!union.add(cp)) multi.add(cp)
            }
        }
        val valid = HashSet(union)
        for (cp in nf.NFD(*union.toIntArray())) valid.add(cp)
        possiblyValid = ReadOnlyIntSet.fromOwnedUnsorted(valid.toIntArray())

        // precompute: unique non-confusables
        val unique = HashSet(union)
        unique.removeAll(multi)
        unique.removeAll(confusables.keys)
        for (cp in unique) confusables[cp] = Whole.UNIQUE_PH

        // precompute: special groups
        LATIN = groups.first { it.name == "Latin" }
        GREEK = groups.first { it.name == "Greek" }
        ASCII = Group(
            -1,
            GroupKind.ASCII,
            "ASCII",
            false,
            ReadOnlyIntSet.fromOwnedUnsorted(possiblyValid.array.filter { it < 0x80 }.toIntArray()),
            ReadOnlyIntSet.EMPTY,
        )
        EMOJI = Group(-1, GroupKind.Emoji, "Emoji", false, ReadOnlyIntSet.EMPTY, ReadOnlyIntSet.EMPTY)
    }

    private fun decodeWholes(dec: Decoder): List<Whole> {
        val ret = ArrayList<Whole>()
        while (true) {
            val confused = dec.readUnique()
            if (confused.isEmpty()) break
            val valid = dec.readUnique()
            val w = Whole(ReadOnlyIntSet.fromOwnedUnsorted(valid), ReadOnlyIntSet.fromOwnedUnsorted(confused))
            for (cp in confused) confusables[cp] = w
            val cover = HashSet<Group>()
            val extents = ArrayList<Extent>()
            val processCp = { cp: Int ->
                val gs = groups.filter { it.contains(cp) }
                val extent = extents.firstOrNull { e -> gs.any { g -> g in e.groups } }
                    ?: Extent().also { extents.add(it) }
                extent.cps.add(cp)
                extent.groups.addAll(gs)
                cover.addAll(gs)
            }
            for (cp in valid) processCp(cp)
            for (cp in confused) processCp(cp)
            for (extent in extents) {
                val complement = cover.filter { it !in extent.groups }.map { it.index }.sorted().toIntArray()
                for (cp in extent.cps) {
                    w.complements[cp] = complement
                }
            }
        }
        return ret
    }

    fun normalize(name: String): String {
        return transform(name, { cps -> outputTokenize(cps, nf::NFC) { e -> e.normalized.array } }) { tokens ->
            val norm = flatten(tokens)
            checkValidLabel(norm, tokens)
            norm
        }
    }

    private inline fun transform(
        name: String,
        tokenizer: (IntArray) -> List<OutputToken>,
        normalizer: (List<OutputToken>) -> IntArray,
    ): String {
        val n = name.length
        if (n == 0) return ""
        val sb = StringBuilder(n + 16)
        var prev = 0
        var more = true
        while (more) {
            var next = name.indexOf(STOP_CH, prev)
            if (next < 0) {
                next = n
                more = false
            }
            val cps = StringUtils.explode(name, prev, next)
            try {
                val tokens = tokenizer(cps)
                for (cp in normalizer(tokens)) {
                    StringUtils.appendCodepoint(sb, cp)
                }
                if (more) sb.append(STOP_CH)
            } catch (e: NormException) {
                throw InvalidLabelException(prev, next, "Invalid label \"${safeImplode(*cps)}\": ${e.message}", e)
            }
            prev = next + 1
        }
        return sb.toString()
    }

    private fun findEmoji(cps: IntArray, startIndex: Int): EmojiResult? {
        var node: EmojiNode? = emojiRoot
        var last: EmojiResult? = null
        var i = startIndex
        val e = cps.size
        while (i < e) {
            val map = node?.map ?: break
            node = map[cps[i++]]
            if (node == null) break
            if (node.emoji != null) {
                last = EmojiResult(i, node.emoji!!)
            }
        }
        return last
    }

    private inline fun outputTokenize(
        cps: IntArray,
        nfFn: (IntArray) -> IntArray,
        emojiStyler: (EmojiSequence) -> IntArray,
    ): List<OutputToken> {
        val tokens = ArrayList<OutputToken>()
        val n = cps.size
        val buf = IntList(n)
        var i = 0
        while (i < n) {
            val match = findEmoji(cps, i)
            if (match != null) {
                if (buf.count > 0) {
                    tokens.add(OutputToken(nfFn(buf.consume()), null))
                    buf.count = 0
                }
                tokens.add(OutputToken(emojiStyler(match.emoji), match.emoji))
                i = match.pos
            } else {
                val cp = cps[i++]
                if (possiblyValid.contains(cp)) {
                    buf.add(cp)
                } else {
                    val replace = mapped[cp]
                    if (replace != null) {
                        buf.add(replace.array)
                    } else if (!ignored.contains(cp)) {
                        throw DisallowedCharacterException(safeCodepoint(cp), cp)
                    }
                }
            }
        }
        if (buf.count > 0) {
            tokens.add(OutputToken(nfFn(buf.consume()), null))
        }
        return tokens
    }

    private fun checkValidLabel(norm: IntArray, tokens: List<OutputToken>): Group {
        if (norm.isEmpty()) {
            throw NormException(EMPTY_LABEL)
        }
        checkLeadingUnderscore(norm)
        val emoji = tokens.size > 1 || tokens[0].emoji != null
        if (!emoji && norm.all { it < 0x80 }) {
            checkLabelExtension(norm)
            return ASCII
        }
        val chars = tokens.filter { it.emoji == null }.flatMap { it.cps.toList() }.toIntArray()
        if (emoji && chars.isEmpty()) {
            return EMOJI
        }
        checkCombiningMarks(tokens)
        checkFenced(norm)
        val unique = chars.toSet().toIntArray()
        val group = determineGroup(unique)
        checkGroup(group, chars)
        checkWhole(group, unique)
        return group
    }

    private fun checkLeadingUnderscore(cps: IntArray) {
        var allowed = true
        for (cp in cps) {
            if (allowed) {
                if (cp != UNDERSCORE) allowed = false
            } else {
                if (cp == UNDERSCORE) throw NormException(INVALID_UNDERSCORE)
            }
        }
    }

    private fun checkLabelExtension(cps: IntArray) {
        if (cps.size >= 4 && cps[2] == HYPHEN && cps[3] == HYPHEN) {
            throw NormException(INVALID_LABEL_EXTENSION, StringUtils.implode(cps.copyOf(4)))
        }
    }

    private fun checkFenced(cps: IntArray) {
        var name = fenced[cps[0]]
        if (name != null) throw NormException(FENCED_LEADING, name)
        val n = cps.size
        var last = -1
        var prev = ""
        for (i in 1 until n) {
            name = fenced[cps[i]]
            if (name != null) {
                if (last == i) throw NormException(FENCED_ADJACENT, "$prev + $name")
                last = i + 1
                prev = name
            }
        }
        if (last == n) throw NormException(FENCED_TRAILING, prev)
    }

    private fun checkCombiningMarks(tokens: List<OutputToken>) {
        for (i in tokens.indices) {
            val t = tokens[i]
            if (t.emoji != null) continue
            val cp = t.cps[0]
            if (combiningMarks.contains(cp)) {
                if (i == 0) {
                    throw NormException(CM_LEADING, safeCodepoint(cp))
                } else {
                    throw NormException(CM_AFTER_EMOJI, "${tokens[i - 1].emoji!!.form} + ${safeCodepoint(cp)}")
                }
            }
        }
    }

    private fun determineGroup(unique: IntArray): Group {
        val gs = groups.toTypedArray()
        var prev = gs.size
        for (cp in unique) {
            var next = 0
            for (i in 0 until prev) {
                if (gs[i].contains(cp)) {
                    gs[next++] = gs[i]
                }
            }
            if (next == 0) {
                if (groups.none { it.contains(cp) }) {
                    throw DisallowedCharacterException(safeCodepoint(cp), cp)
                } else {
                    throw createMixtureException(gs[0], cp)
                }
            }
            prev = next
            if (prev == 1) break
        }
        return gs[0]
    }

    private fun checkGroup(group: Group, cps: IntArray) {
        for (cp in cps) {
            if (!group.contains(cp)) throw createMixtureException(group, cp)
        }
        if (group.cmWhitelisted) return
        val decomposed = nf.NFD(*cps)
        var i = 1
        val e = decomposed.size
        while (i < e) {
            if (nonSpacingMarks.contains(decomposed[i])) {
                var j = i + 1
                while (j < e && nonSpacingMarks.contains(decomposed[j])) {
                    val cp = decomposed[j]
                    for (k in i until j) {
                        if (decomposed[k] == cp) throw NormException(NSM_DUPLICATE, safeCodepoint(cp))
                    }
                    j++
                }
                val n = j - i
                if (n > maxNonSpacingMarks) {
                    throw NormException(
                        NSM_EXCESSIVE,
                        "${safeImplode(*decomposed.copyOfRange(i - 1, j))} ($n/$maxNonSpacingMarks)",
                    )
                }
                i = j
            }
            i++
        }
    }

    private fun checkWhole(group: Group, unique: IntArray) {
        var bound = 0
        var maker: IntArray? = null
        val shared = IntList(unique.size)
        for (cp in unique) {
            val w = confusables[cp]
            if (w == null) {
                shared.add(cp)
            } else if (w === Whole.UNIQUE_PH) {
                return
            } else {
                val comp = w.complements[cp]!!
                if (bound == 0) {
                    maker = comp.copyOf()
                    bound = comp.size
                } else {
                    var b = 0
                    for (i in 0 until bound) {
                        if (comp.binarySearch(maker!![i]) >= 0) {
                            maker[b++] = maker[i]
                        }
                    }
                    bound = b
                }
                if (bound == 0) return
            }
        }
        if (bound > 0) {
            for (i in 0 until bound) {
                val other = groups[maker!![i]]
                if (shared.stream().all { other.contains(it) }) {
                    throw ConfusableException(group, other)
                }
            }
        }
    }

    private fun createMixtureException(group: Group, cp: Int): IllegalMixtureException {
        var conflict = safeCodepoint(cp)
        val other = groups.firstOrNull { it.primary.contains(cp) }
        if (other != null) {
            conflict = "$other $conflict"
        }
        return IllegalMixtureException("$group + $conflict", cp, group, other)
    }

    fun safeCodepoint(cp: Int): String {
        val sb = StringBuilder()
        if (!shouldEscape.contains(cp)) {
            sb.append('"')
            safeImplode(sb, intArrayOf(cp))
            sb.append('"')
            sb.append(' ')
        }
        appendHexEscape(sb, cp)
        return sb.toString()
    }

    fun safeImplode(vararg cps: Int): String {
        val sb = StringBuilder(cps.size + 16)
        safeImplode(sb, cps)
        return sb.toString()
    }

    fun safeImplode(sb: StringBuilder, cps: IntArray) {
        if (cps.isEmpty()) return
        if (combiningMarks.contains(cps[0])) {
            StringUtils.appendCodepoint(sb, 0x25CC)
        }
        for (cp in cps) {
            if (shouldEscape.contains(cp)) {
                appendHexEscape(sb, cp)
            } else {
                StringUtils.appendCodepoint(sb, cp)
            }
        }
        StringUtils.appendCodepoint(sb, 0x200E)
    }

    private class EmojiNode {
        var emoji: EmojiSequence? = null
        var map: HashMap<Int, EmojiNode>? = null

        fun then(cp: Int): EmojiNode {
            val m = map ?: HashMap<Int, EmojiNode>().also { map = it }
            return m.getOrPut(cp) { EmojiNode() }
        }
    }

    private class EmojiResult(val pos: Int, val emoji: EmojiSequence)

    private class Extent {
        val groups = HashSet<Group>()
        val cps = ArrayList<Int>()
    }

    companion object {
        const val DISALLOWED_CHARACTER = "disallowed character"
        const val ILLEGAL_MIXTURE = "illegal mixture"
        const val WHOLE_CONFUSABLE = "whole-script confusable"
        const val EMPTY_LABEL = "empty label"
        const val NSM_DUPLICATE = "duplicate non-spacing marks"
        const val NSM_EXCESSIVE = "excessive non-spacing marks"
        const val CM_LEADING = "leading combining mark"
        const val CM_AFTER_EMOJI = "emoji + combining mark"
        const val FENCED_LEADING = "leading fenced"
        const val FENCED_ADJACENT = "adjacent fenced"
        const val FENCED_TRAILING = "trailing fenced"
        const val INVALID_LABEL_EXTENSION = "invalid label extension"
        const val INVALID_UNDERSCORE = "underscore allowed only at start"

        private const val STOP_CH = '.'
        private const val UNDERSCORE = 0x5F
        private const val HYPHEN = 0x2D

        private fun appendHexEscape(sb: StringBuilder, cp: Int) {
            sb.append('{')
            StringUtils.appendHex(sb, cp)
            sb.append('}')
        }

        private fun flatten(tokens: List<OutputToken>): IntArray {
            return tokens.flatMap { it.cps.toList() }.toIntArray()
        }

        private fun decodeNamedCodepoints(dec: Decoder): Map<Int, String> {
            val ret = HashMap<Int, String>()
            for (cp in dec.readSortedAscending(dec.readUnsigned())) {
                ret[cp] = dec.readString()
            }
            return ret
        }

        private fun decodeMapped(dec: Decoder): Map<Int, ReadOnlyIntList> {
            val ret = HashMap<Int, ReadOnlyIntList>()
            while (true) {
                val w = dec.readUnsigned()
                if (w == 0) break
                val keys = dec.readSortedUnique()
                val n = keys.size
                val m = Array(n) { IntArray(w) }
                for (j in 0 until w) {
                    val v = dec.readUnsortedDeltas(n)
                    for (i in 0 until n) m[i][j] = v[i]
                }
                for (i in 0 until n) {
                    ret[keys[i]] = ReadOnlyIntList(m[i])
                }
            }
            return ret
        }

        private fun decodeGroups(dec: Decoder): List<Group> {
            val ret = ArrayList<Group>()
            while (true) {
                val name = dec.readString()
                if (name.isEmpty()) break
                val bits = dec.readUnsigned()
                val kind = if ((bits and 1) != 0) GroupKind.Restricted else GroupKind.Script
                val cm = (bits and 2) != 0
                ret.add(
                    Group(
                        ret.size,
                        kind,
                        name,
                        cm,
                        ReadOnlyIntSet.fromOwnedUnsorted(dec.readUnique()),
                        ReadOnlyIntSet.fromOwnedUnsorted(dec.readUnique()),
                    ),
                )
            }
            return ret
        }
    }
}
