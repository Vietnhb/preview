import test from "node:test";
import assert from "node:assert/strict";
import { appendDiscussionPage } from "../src/features/library/model/discussionModel.ts";

const comment = id => ({ id, body: id });
const current = { likes: 8, liked: true, commentCount: 23, commentMaxLength: 2000, canInteract: true, page: 0, hasMore: true, comments: [comment("posted"), comment("first"), comment("boundary")] };

test("pagination deduplicates shifted rows after posting a comment without losing the new comment", () => {
  const result = appendDiscussionPage(current, { ...current, page: 1, hasMore: false, comments: [comment("boundary"), comment("last")] });
  assert.deepEqual(result.comments.map(item => item.id), ["posted", "first", "boundary", "last"]);
  assert.equal(result.page, 1);
  assert.equal(result.hasMore, false);
});

test("an older page snapshot cannot overwrite current reactions, counts or interaction permission", () => {
  const result = appendDiscussionPage(current, { ...current, likes: 3, liked: false, commentCount: 22, canInteract: false, page: 1, comments: [comment("next")] });
  assert.equal(result.likes, 8);
  assert.equal(result.liked, true);
  assert.equal(result.commentCount, 23);
  assert.equal(result.canInteract, true);
});

test("an already loaded or stale page is ignored", () => {
  const loaded = appendDiscussionPage(current, { ...current, page: 1, comments: [comment("next")] });
  assert.equal(appendDiscussionPage(loaded, { ...current, page: 0, comments: [comment("stale")] }), loaded);
  assert.equal(appendDiscussionPage(loaded, { ...current, page: 1, comments: [comment("duplicate")] }), loaded);
});
