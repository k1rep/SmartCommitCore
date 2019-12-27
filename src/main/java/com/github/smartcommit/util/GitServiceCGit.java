package com.github.smartcommit.util;

import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.constant.ChangeType;
import com.github.smartcommit.model.constant.FileStatus;
import com.github.smartcommit.model.constant.FileType;
import com.github.smartcommit.model.constant.Version;
import io.reflectoring.diffparser.api.DiffParser;
import io.reflectoring.diffparser.api.UnifiedDiffParser;
import io.reflectoring.diffparser.api.model.Diff;
import io.reflectoring.diffparser.api.model.Hunk;
import io.reflectoring.diffparser.api.model.Line;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Implementation of helper functions based on the output of git commands. */
public class GitServiceCGit implements GitService {
  /**
   * Get the diff files in the current working tree
   *
   * @return
   */
  @Override
  public ArrayList<DiffFile> getChangedFilesInWorkingTree(String repoDir) {
    ArrayList<DiffFile> diffFileList = new ArrayList<>();
    // run git status --porcelain to get changeset
    String output = Utils.runSystemCommand(repoDir, "git", "status", "--porcelain");
    if (output.isEmpty()) {
      // working tree clean
      return diffFileList;
    }

    String lines[] = output.split("\\r?\\n");
    for (int i = 0; i < lines.length; i++) {
      String temp[] = lines[i].trim().split("\\s+");
      String symbol = temp[0];
      String relativePath = temp[1];
      FileType fileType = Utils.checkFileType(relativePath);
      String absolutePath = repoDir + File.separator + relativePath;
      FileStatus status = Utils.convertSymbolToStatus(symbol);
      DiffFile DiffFile = null;
      switch (status) {
        case MODIFIED:
          DiffFile =
              new DiffFile(
                  i,
                  status,
                  fileType,
                  relativePath,
                  relativePath,
                  getContentAtHEAD(repoDir, relativePath),
                  Utils.readFileToString(absolutePath));
          break;
        case ADDED:
        case UNTRACKED:
          DiffFile =
              new DiffFile(
                  i, status, fileType, "", relativePath, "", Utils.readFileToString(absolutePath));
          break;
        case DELETED:
          DiffFile =
              new DiffFile(
                  i,
                  status,
                  fileType,
                  relativePath,
                  "",
                  getContentAtHEAD(repoDir, relativePath),
                  "");
          break;
        case RENAMED:
        case COPIED:
          if (temp.length == 4) {
            String oldPath = temp[1];
            String newPath = temp[3];
            String newAbsPath = repoDir + File.separator + temp[3];
            DiffFile =
                new DiffFile(
                    i,
                    status,
                    fileType,
                    oldPath,
                    newPath,
                    getContentAtHEAD(repoDir, oldPath),
                    Utils.readFileToString(newAbsPath));
          }
          break;
        default:
          break;
      }
      if (DiffFile != null) {
        diffFileList.add(DiffFile);
      }
    }
    return diffFileList;
  }

  /**
   * Get the diff files between one commit and its previous commit
   *
   * @return
   */
  @Override
  public ArrayList<DiffFile> getChangedFilesAtCommit(String repoDir, String commitID) {
    // git diff <start_commit> <end_commit>
    // on Windows the ~ character must be used instead of ^
    String output =
        Utils.runSystemCommand(repoDir, "git", "diff", "--name-status", commitID + "~", commitID);
    ArrayList<DiffFile> DiffFileList = new ArrayList<>();
    String lines[] = output.split("\\r?\\n");
    for (int i = 0; i < lines.length; i++) {
      String temp[] = lines[i].trim().split("\\s+");
      String symbol = temp[0];
      String relativePath = temp[1];
      FileType fileType = Utils.checkFileType(relativePath);
      //            String absolutePath = repoDir + File.separator + relativePath;
      FileStatus status = Utils.convertSymbolToStatus(symbol);
      DiffFile DiffFile = null;
      switch (status) {
        case MODIFIED:
          DiffFile =
              new DiffFile(
                  i,
                  status,
                  fileType,
                  relativePath,
                  relativePath,
                  getContentAtCommit(repoDir, relativePath, commitID + "~"),
                  getContentAtCommit(repoDir, relativePath, commitID));
          break;
        case ADDED:
        case UNTRACKED:
          DiffFile =
              new DiffFile(
                  i,
                  status,
                  fileType,
                  "",
                  relativePath,
                  "",
                  getContentAtCommit(repoDir, relativePath, commitID));
          break;
        case DELETED:
          DiffFile =
              new DiffFile(
                  i,
                  status,
                  fileType,
                  relativePath,
                  "",
                  getContentAtCommit(repoDir, relativePath, commitID + "~"),
                  "");
          break;
        case RENAMED:
        case COPIED:
          if (temp.length == 4) {
            String oldPath = temp[1];
            String newPath = temp[3];
            DiffFile =
                new DiffFile(
                    i,
                    status,
                    fileType,
                    oldPath,
                    newPath,
                    getContentAtCommit(repoDir, oldPath, commitID + "~"),
                    getContentAtCommit(repoDir, newPath, commitID));
          }
          break;
        default:
          break;
      }
      if (DiffFile != null) {
        DiffFileList.add(DiffFile);
      }
    }
    return DiffFileList;
  }

  @Override
  public List<DiffHunk> getDiffHunksInWorkingTree(String repoPath, List<DiffFile> diffFiles) {
    String diffOutput = Utils.runSystemCommand(repoPath, "git", "diff", "-U1");
    DiffParser parser = new UnifiedDiffParser();
    List<Diff> diffs = parser.parse(new ByteArrayInputStream(diffOutput.getBytes()));
    return generateDiffHunks(diffs, diffFiles);
  }

  /**
   * Generate diff hunks from diffs parsed from git-diff output
   *
   * @param diffs
   * @return
   */
  private List<DiffHunk> generateDiffHunks(List<Diff> diffs, List<DiffFile> diffFiles) {
    List<DiffHunk> allDiffHunks = new ArrayList<>();
    // one file, one diff
    for (Diff diff : diffs) {
      // the hunkIndex of the diff hunk in the current file diff, start from 0
      Integer hunkIndex = 0;

      String baseFilePath = diff.getFromFileName();
      String currentFilePath = diff.getToFileName();
      // currently we only process Java files
      FileType fileType =
          baseFilePath.contains("/dev/null")
              ? Utils.checkFileType(currentFilePath)
              : Utils.checkFileType(baseFilePath);

      // collect and save diff hunks into diff files
      List<DiffHunk> diffHunksInFile = new ArrayList<>();
      for (Hunk hunk : diff.getHunks()) {
        List<String> baseCodeLines = getCodeSnippetInHunk(hunk.getLines(), Version.BASE);
        List<String> currentCodeLines = getCodeSnippetInHunk(hunk.getLines(), Version.CURRENT);
        com.github.smartcommit.model.Hunk baseHunk =
            new com.github.smartcommit.model.Hunk(
                Version.BASE,
                removeVersionLabel(baseFilePath),
                hunk.getFromFileRange().getLineStart() + 1,
                hunk.getFromFileRange().getLineStart() + hunk.getFromFileRange().getLineCount() - 2,
                Utils.checkContentType(baseCodeLines),
                baseCodeLines);
        com.github.smartcommit.model.Hunk currentHunk =
            new com.github.smartcommit.model.Hunk(
                Version.CURRENT,
                removeVersionLabel(currentFilePath),
                hunk.getToFileRange().getLineStart() + 1,
                hunk.getToFileRange().getLineStart() + hunk.getToFileRange().getLineCount() - 2,
                Utils.checkContentType(currentCodeLines),
                currentCodeLines);
        ChangeType changeType = ChangeType.MODIFIED;
        if (baseCodeLines.isEmpty()) {
          changeType = ChangeType.ADDED;
        }
        if (currentCodeLines.isEmpty()) {
          changeType = ChangeType.DELETED;
        }
        DiffHunk diffHunk =
            new DiffHunk(hunkIndex, fileType, changeType, baseHunk, currentHunk, "");
        diffHunksInFile.add(diffHunk);
        allDiffHunks.add(diffHunk);
        hunkIndex++;
      }

      for (DiffFile diffFile : diffFiles) {
        if (baseFilePath.contains(diffFile.getBaseRelativePath())
            && currentFilePath.contains(diffFile.getCurrentRelativePath())) {
          diffHunksInFile.forEach(diffHunk -> diffHunk.setFileIndex(diffFile.getIndex()));
          diffFile.setDiffHunks(diffHunksInFile);
        }
      }
    }
    return allDiffHunks;
  }

  /**
   * Get code snippet from lines in Hunk
   *
   * @param lines
   * @param version
   * @return
   */
  private List<String> getCodeSnippetInHunk(List<Line> lines, Version version) {
    List<String> linesContent = new ArrayList<>();
    if (version.equals(Version.BASE)) {
      lines.stream()
          .filter(line -> line.getLineType().equals(Line.LineType.FROM))
          .forEach(line -> linesContent.add(line.getContent()));
    } else if (version.equals(Version.CURRENT)) {
      lines.stream()
          .filter(line -> line.getLineType().equals(Line.LineType.TO))
          .forEach(line -> linesContent.add(line.getContent()));
    }
    return linesContent;
  }

  /**
   * Get the diff hunks between one commit and its previous commit
   *
   * @param repoPath
   * @param commitID
   * @return
   */
  @Override
  public List<DiffHunk> getDiffHunksAtCommit(
      String repoPath, String commitID, List<DiffFile> diffFiles) {
    // git diff <start_commit> <end_commit>
    // on Windows the ~ character must be used instead of ^
    String diffOutput =
        Utils.runSystemCommand(repoPath, "git", "diff", "-U1", commitID + "~", commitID);
    DiffParser parser = new UnifiedDiffParser();
    // TODO fix the bug within the library when parsing diff with only added lines with -U0 or
    // default -U3
    List<Diff> diffs = parser.parse(new ByteArrayInputStream(diffOutput.getBytes()));
    return generateDiffHunks(diffs, diffFiles);
  }

  /**
   * Get the file content at HEAD
   *
   * @param relativePath
   * @return
   */
  @Override
  public String getContentAtHEAD(String repoDir, String relativePath) {
    String output = Utils.runSystemCommand(repoDir, "git", "show", "HEAD:" + relativePath);
    if (output != null) {
      return output;
    } else {
      return "";
    }
  }

  /**
   * Get the file content at one specific commit
   *
   * @param relativePath
   * @returnØØ
   */
  @Override
  public String getContentAtCommit(String repoDir, String relativePath, String commitID) {
    String output = Utils.runSystemCommand(repoDir, "git", "show", commitID + ":" + relativePath);
    if (output != null) {
      return output;
    } else {
      return "";
    }
  }

  /**
   * Remove the "a/" or "b/" at the beginning of the path printed in Git
   *
   * @return
   */
  private String removeVersionLabel(String gitFilePath) {
    return gitFilePath.replaceFirst("a/", "").replaceFirst("b/", "");
  }
}
