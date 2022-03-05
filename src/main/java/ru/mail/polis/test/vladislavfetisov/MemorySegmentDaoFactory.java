package ru.mail.polis.test.vladislavfetisov;

import jdk.incubator.foreign.MemorySegment;
import ru.mail.polis.Config;
import ru.mail.polis.Dao;
import ru.mail.polis.Entry;
import ru.mail.polis.test.DaoFactory;
import ru.mail.polis.vladislavfetisov.InMemoryDao;

import java.nio.charset.StandardCharsets;

@DaoFactory(stage = 2)
public class MemorySegmentDaoFactory implements DaoFactory.Factory<MemorySegment, Entry<MemorySegment>> {
    @Override
    public Dao<MemorySegment, Entry<MemorySegment>> createDao(Config config) {
        return new InMemoryDao(config);
    }

    @Override
    public String toString(MemorySegment data) {
        return (data == null) ? null : new String(data.toByteArray(), StandardCharsets.UTF_8);
    }

    @Override
    public MemorySegment fromString(String data) {
        return (data == null) ? null : MemorySegment.ofArray(data.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Entry<MemorySegment> fromBaseEntry(Entry<MemorySegment> baseEntry) {
        return baseEntry;
    }
}
