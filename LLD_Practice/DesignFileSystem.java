package LLD_Practice;

import java.util.HashMap;
import java.util.Map;

/*
Requirements
1. User should be able to create directory
2. User should be able to create file
3. move a file / dir from 1 directory to another
4. delete directory
5. delete file
6. ls 
*/

public class DesignFileSystem {

  static interface FileSystemNode {
    String getName();
  }

  static class File implements FileSystemNode {
    String name;
    String content;

    File(String name, String content) {
      this.name = name;
      this.content = content;
    }

    @Override
    public String getName() {
      return name;
    }
  }

  static class Dir implements FileSystemNode {
    String name;
    Dir parent;

    Map<String, FileSystemNode> children;

    Dir(String name) {
      this.name = name;
      this.children = new HashMap<>();
    }

    @Override
    public String getName() {
      return this.name;
    }
  }

  public static class FileSystemManager {
    private final Dir root = new Dir("/");

    public Dir getRoot() {
      return root;
    }

    private Dir resolvePath(String path) {
      if (path == null || path.isBlank()) {
        throw new IllegalStateException("Invalid dir or path");
      }
      Dir curr = root;
      String pathArr[] = path.split("/");

      for (String segment : pathArr) {
        if (segment.isBlank()) {
          continue;
        }

        FileSystemNode node = curr.children.get(segment);

        if (!(node instanceof Dir)) {
          throw new IllegalArgumentException("Directory not found: " + path);
        }

        curr = (Dir) node;
      }
      return curr;
    }

    private void dirValidation(String name) {
      if (name == null || name.isBlank() || name.contains("/")) {
        throw new IllegalArgumentException("Invalid dir or path");
      }
    }

    public void createDirectory(String parentPath, String dirName) {
      dirValidation(dirName);

      Dir curr = resolvePath(parentPath);

      if (curr.children.containsKey(dirName)) {
        throw new IllegalArgumentException("Duplicate Directory ...");
      }
      Dir newDir = new Dir(dirName);
      newDir.parent = curr;
      curr.children.put(dirName, newDir);
    }

    private void fileValidation(String name) {
      if (name == null || name.isBlank() || name.contains("/")) {
        throw new IllegalArgumentException("Invalid file name");
      }
    }

    public void createFile(String parentPath, String fileName, String content) {
      fileValidation(fileName);
      Dir curr = resolvePath(parentPath);
      if (curr.children.containsKey(fileName)) {
        throw new IllegalArgumentException("File already exists with name " + fileName);
      }
      File file = new File(fileName, content);
      curr.children.put(fileName, file);
    }

    public void deleteFile(String parentPath, String fileName) {
      fileValidation(fileName);
      Dir curr = resolvePath(parentPath);
      FileSystemNode node = curr.children.get(fileName);
      if (node == null) {
        throw new IllegalArgumentException("File not found.");
      }
      if (!(node instanceof File)) {
        throw new IllegalArgumentException("Not a file");
      }
      curr.children.remove(fileName);
    }

    public void deleteDir(String parentPath, String dirName) {
      dirValidation(dirName);
      Dir curr = resolvePath(parentPath);
      FileSystemNode node = curr.children.get(dirName);
      if (node == null) {
        throw new IllegalArgumentException("Dir not found");
      }
      if (!(node instanceof Dir)) {
        throw new IllegalArgumentException("Not a dir");
      }
      curr.children.remove(dirName);
    }

    public void ls(String path) {
      Dir curr = resolvePath(path);
      for (Map.Entry<String, FileSystemNode> entry : curr.children.entrySet()) {
        String key = entry.getKey();
        FileSystemNode val = entry.getValue();
        if (val instanceof File) {
          System.out.println("File: " + key);
        } else {
          System.out.println("Dir: " + key);
        }
      }
    }

    public void move(String srcPath, String destPath) {
      Dir srcDir = resolvePath(srcPath);
      Dir destDir = resolvePath(destPath);

      if (srcDir == root) {
        throw new IllegalArgumentException("Can't move root dir to any other dir");
      }

      // Duplicate entry at destination
      if (destDir.children.containsKey(srcDir.getName())) {
        throw new IllegalArgumentException("Dir with same name already present at destination");
      }

      Dir srcParent = srcDir.parent;

      // Prevent moving a dir into self or descendant
      Dir curr = destDir;
      while (curr != null) {
        if (curr == srcDir) {
          throw new IllegalArgumentException("Operation not allowed");
        }
        curr = curr.parent;
      }

      srcParent.children.remove(srcDir.getName());
      destDir.children.put(srcDir.getName(), srcDir);
      srcDir.parent = destDir;
    }
  }

  public static void main(String[] args) {
    //
  }
}
