package com.github.smartcommit.model.constant;

/**
 * Status of a DiffFile
 */
public enum FileStatus {
  // XY: two-letter status code, where X shows the index status, Y shows the working tree status
  UNMODIFIED(" ", "unmodified"), // usually won't appear
  MODIFIED("M", "modified"),
  ADDED("A", "added"),
  DELETED("D", "deleted"),
  RENAMED("R", "renamed"),
  COPIED("C", "copied"),
  UNMERGED("U", "unmerged"),
  UNTRACKED("??", "untracked"),
  IGNORED("!!", "ignored"); // Ignored files are not listed, unless --ignored option is in effect, in which case XY are !!.

  public String symbol;
  public String label;

  FileStatus(String symbol, String label) {
    this.symbol = symbol;
    this.label = label;
  }
}
