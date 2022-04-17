package ru.mail.polis.vladislavfetisov;

import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import ru.mail.polis.BaseEntry;
import ru.mail.polis.Entry;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentNavigableMap;

public final class Utils {

    private Utils() {

    }

    public static long sizeOfEntry(Entry<MemorySegment> entry) {
        long valueSize = entry.isTombstone() ? 0 : entry.value().byteSize();
        return 2L * Long.BYTES + entry.key().byteSize() + valueSize;
    }

    public static int compareMemorySegments(MemorySegment o1, MemorySegment o2) {
        long mismatch = o1.mismatch(o2);
        if (mismatch == -1) {
            return 0;
        }
        if (mismatch == o1.byteSize()) {
            return -1;
        }
        if (mismatch == o2.byteSize()) {
            return 1;
        }
        byte b1 = MemoryAccess.getByteAtOffset(o1, mismatch);
        byte b2 = MemoryAccess.getByteAtOffset(o2, mismatch);
        return Byte.compare(b1, b2);
    }

    public static long binarySearch(MemorySegment key,
                                    MemorySegment mapFile,
                                    MemorySegment mapIndex) {
        long l = 0;
        long rightBound = mapIndex.byteSize() / Long.BYTES;
        long r = rightBound - 1;
        while (l <= r) {
            long middle = (l + r) >>> 1;
            Entry<MemorySegment> middleEntry = getByIndex(mapFile, mapIndex, middle);
            int res = compareMemorySegments(middleEntry.key(), key);
            if (res == 0) {
                return middle;
            } else if (res < 0) {
                l = middle + 1;
            } else {
                r = middle - 1;
            }
        }
        if (r == -1) {
            return -(l + 1);
        }
        return l;
    }

    public static Entry<MemorySegment> getByIndex(MemorySegment mapFile, MemorySegment mapIndex, long index) {
        long offset = getLength(mapIndex, index * Long.BYTES);

        long keyLength = getLength(mapFile, offset);
        offset += Long.BYTES;
        MemorySegment key = mapFile.asSlice(offset, keyLength);

        offset += keyLength;
        long valueLength = getLength(mapFile, offset);
        MemorySegment value;
        if (valueLength == SSTable.NULL_VALUE) {
            value = null;
        } else {
            value = mapFile.asSlice(offset + Long.BYTES, valueLength);
        }
        return new BaseEntry<>(key, value);
    }

    private static long getLength(MemorySegment mapFile, long offset) {
        return MemoryAccess.getLongAtOffset(mapFile, offset);
    }

    public static long writeSegment(MemorySegment segment, MemorySegment fileMap, long fileOffset) {
        long length = segment.byteSize();
        MemoryAccess.setLongAtOffset(fileMap, fileOffset, length);

        fileMap.asSlice(fileOffset + Long.BYTES).copyFrom(segment);

        return Long.BYTES + length;
    }

    public static MemorySegment map(Path table,
                                    long length,
                                    FileChannel.MapMode mapMode,
                                    ResourceScope scope) throws IOException {
        return MemorySegment.mapFile(table, 0, length, mapMode, scope);
    }

    public static void rename(Path source, Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    public static Path withSuffix(Path path, String suffix) {
        return path.resolveSibling(path.getFileName() + suffix);
    }

    public static void deleteTablesToIndex(List<SSTable> tableList, int toIndex) throws IOException {
        for (int i = 0; i < toIndex; i++) {
            SSTable table = tableList.get(i);
            Files.deleteIfExists(table.getTableName());
            Files.deleteIfExists(table.getIndexName());
        }
    }

    public static SSTable.Sizes getSizes(Iterator<Entry<MemorySegment>> values) {
        long tableSize = 0;
        long count = 0;
        while (values.hasNext()) {
            tableSize += sizeOfEntry(values.next());
            count++;
        }
        return new SSTable.Sizes(tableSize, count * Long.BYTES);
    }

    public static String removeSuffix(String source, String suffix) {
        return source.substring(0, source.length() - suffix.length());
    }

    public static Iterator<Entry<MemorySegment>> tablesRange(
            MemorySegment from, MemorySegment to, List<SSTable> tables) {
        List<Iterator<Entry<MemorySegment>>> iterators = new ArrayList<>(tables.size());
        for (SSTable table : tables) {
            iterators.add(table.range(from, to));
        }
        return CustomIterators.merge(iterators);
    }

    public static Iterator<Entry<MemorySegment>> fromMemory(
            MemorySegment from,
            MemorySegment to,
            ConcurrentNavigableMap<MemorySegment, Entry<MemorySegment>> storage) {

        if (from == null && to == null) {
            return storage.values().iterator();
        }
        return subMap(from, to, storage).values().iterator();
    }

    public static ConcurrentNavigableMap<MemorySegment, Entry<MemorySegment>> subMap(
            MemorySegment from,
            MemorySegment to,
            ConcurrentNavigableMap<MemorySegment, Entry<MemorySegment>> storage) {

        if (from == null) {
            return storage.headMap(to);
        }
        if (to == null) {
            return storage.tailMap(from);
        }
        return storage.subMap(from, to);
    }
}
