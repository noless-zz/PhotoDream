# PhotoDream roadmap

Each line is a GitHub issue written so Claude Code can implement it (see `docs/CLAUDE_CODE.md`).
Work top to bottom; arrows show what must come first.
✅ = implemented on branch `claude/gifted-newton-vhf8uj` (not yet merged / device-tested). #23 waits for a decision; #20 and #24 are epics.

## v0.3 · Sources & photo table  ([milestone](https://github.com/noless-zz/PhotoDream/milestone/1))
| # | Issue | Size | Needs |
|---|---|---|---|
| [#1](https://github.com/noless-zz/PhotoDream/issues/1) | ✅ Photo model: every photo knows its source (foundation) | M | – |
| [#2](https://github.com/noless-zz/PhotoDream/issues/2) | ✅ Manage sources: show/hide each source, remove the phone folder | M | #1 |
| [#3](https://github.com/noless-zz/PhotoDream/issues/3) | ✅ Frame color per source | M | #1 |
| [#4](https://github.com/noless-zz/PhotoDream/issues/4) | ✅ Double-tap: focus one photo / back; double-tap black area to exit | M | – |
| [#5](https://github.com/noless-zz/PhotoDream/issues/5) | ✅ Several phone folders | M | #2 |

## Cleanup & quality  ([milestone](https://github.com/noless-zz/PhotoDream/milestone/2))
| # | Issue | Size | Needs |
|---|---|---|---|
| [#6](https://github.com/noless-zz/PhotoDream/issues/6) | ✅ Docs cleanup – **good first test of `@claude`** | S | – |
| [#7](https://github.com/noless-zz/PhotoDream/issues/7) | ✅ Plurals for counts (English + Hebrew) | S | – |
| [#8](https://github.com/noless-zz/PhotoDream/issues/8) | ✅ Backup rules: never back up tokens or the photo cache | S | – |
| [#9](https://github.com/noless-zz/PhotoDream/issues/9) | ✅ Accessibility and remaining lint warnings | S | – |
| [#10](https://github.com/noless-zz/PhotoDream/issues/10) | ✅ Shared photo queue + loader for both display modes | M | #1 |
| [#11](https://github.com/noless-zz/PhotoDream/issues/11) | ✅ Sync: stop re-downloading photos that can't be decoded | S | – |
| [#12](https://github.com/noless-zz/PhotoDream/issues/12) | ✅ Settings in sections + About page (do before #16) | M | – |
| [#13](https://github.com/noless-zz/PhotoDream/issues/13) | ✅ "Send problem report" for student support | S | – |

## v0.4 · Wake-up challenge alarm  ([epic #20](https://github.com/noless-zz/PhotoDream/issues/20))
An alarm that rings over the lock screen; you stop it by solving a challenge with your own photos.
| # | Issue | Size | Needs |
|---|---|---|---|
| [#14](https://github.com/noless-zz/PhotoDream/issues/14) | ✅ Data model, storage, exact scheduling (tested time math, DST) | L | – |
| [#15](https://github.com/noless-zz/PhotoDream/issues/15) | ✅ Ringing screen, sound ramp, snooze, safety fallback, challenge host | L | #14 |
| [#16](https://github.com/noless-zz/PhotoDream/issues/16) | ✅ Alarm list + editor | M | #14, #12 |
| [#17](https://github.com/noless-zz/PhotoDream/issues/17) | ✅ Challenge: Flip them all | M | #15 |
| [#18](https://github.com/noless-zz/PhotoDream/issues/18) | ✅ Challenge: Catch the runaway photo | M | #15 |
| [#19](https://github.com/noless-zz/PhotoDream/issues/19) | ✅ Challenge: Memory pairs | M | #17 |

## v0.5 · AI find-the-photo  ([epic #24](https://github.com/noless-zz/PhotoDream/issues/24))
| # | Issue | Size | Needs |
|---|---|---|---|
| [#21](https://github.com/noless-zz/PhotoDream/issues/21) | ✅ On-device descriptions: ML Kit labels (all phones), Gemini Nano sentences (newer phones), Hebrew, cache | L | #1 |
| [#22](https://github.com/noless-zz/PhotoDream/issues/22) | ✅ Challenge: Find the described photo | M | #15, #21 |
| [#23](https://github.com/noless-zz/PhotoDream/issues/23) | ✅ Optional cloud descriptions: Claude, ChatGPT or Gemini (own API key, daily cap) | M | #21 |

## v0.6 · Delight  ([milestone](https://github.com/noless-zz/PhotoDream/milestone/5))
| # | Issue | Size | Needs |
|---|---|---|---|
| [#25](https://github.com/noless-zz/PhotoDream/issues/25) | ✅ Photo dates: "On this day" + date captions | L | #10 |
| [#26](https://github.com/noless-zz/PhotoDream/issues/26) | ✅ Favorite or hide a photo from focus mode | M | #4, #10 |
| [#27](https://github.com/noless-zz/PhotoDream/issues/27) | ✅ Night mode schedule | M | – |
| [#28](https://github.com/noless-zz/PhotoDream/issues/28) | ✅ Burn-in protection for the clock | S | – |
| [#29](https://github.com/noless-zz/PhotoDream/issues/29) | ✅ Face-aware cropping and Ken Burns | M | – |
| [#30](https://github.com/noless-zz/PhotoDream/issues/30) | ✅ "Shared with me" folders (class albums) | M | – |
| [#31](https://github.com/noless-zz/PhotoDream/issues/31) | ✅ Tell users when a new version is on GitHub | S | – |
| [#32](https://github.com/noless-zz/PhotoDream/issues/32) | ✅ Home-screen photo widget | M | – |

## Later / only if going public
Live wallpaper version of the photo table · Google Play readiness (privacy policy page, user-initiated transfer
job instead of the foreground sync, no battery-exemption button, Drive verification + yearly security assessment).
