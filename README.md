# File System Organizer

A command-line tool that scans a directory tree, finds duplicate files, and lets you report, move, or delete them.

## How it works

- Traverses directories using BFS
- Hashes each file in chunks using Rabin-Karp rolling hash, then confirms duplicates with SHA-256
- Caches file metadata in a segmented LRU cache to avoid redundant disk reads
- Parallelizes file reading and hashing across a fixed worker thread pool, coordinated via a bounded blocking queue

## Requirements

- Java 17+
- Maven 3.6+

## Build

```bash
mvn package -DskipTests
```

The jar is produced at `target/concurrent-file-system-organizer-1.0.0.jar`.

## Run

```bash
java -jar target/concurrent-file-system-organizer-1.0.0.jar <directory> [REPORT|MOVE|DELETE]
```

| Mode | Effect |
|---|---|
| `REPORT` | Prints duplicate groups. Nothing is modified. (default) |
| `MOVE` | Moves duplicate copies to `./cfs-output/`. One copy per group is kept in place. |
| `DELETE` | Permanently deletes duplicate copies. One copy per group is kept in place. |

**Always run `REPORT` first** to review what will be affected before using `MOVE` or `DELETE`.

## Example

```bash
java -jar target/concurrent-file-system-organizer-1.0.0.jar ~/demo REPORT
```

![Sample run](docs/demo-screenshot.png)

## Run tests

```bash
mvn test
```

## Project structure

```
src/main/java/com/cfs/
  Main.java               entry point
  config/Config.java      tunable constants (thread count, cache size, etc.)
  traversal/              BFS traverser and FileTask value object
  queue/                  bounded blocking queue
  worker/                 thread pool + consumer worker
  hash/                   rolling hasher + duplicate registry
  cache/                  segmented LRU cache
  organizer/              REPORT / MOVE / DELETE logic
```
