package com.doppelganger113.commandrunner;

import de.siegmar.fastcsv.reader.CommentStrategy;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRecord;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Reader;
import java.util.List;

@Service
public class CsvService {
    private Person mapEntry(CsvRecord record) {
        return new Person(
                Long.parseLong(record.getField(0)),
                Float.parseFloat(record.getField(1)),
                Float.parseFloat(record.getField(2))
        );
    }

    public List<Person> readFile(Reader readable) {
        try (CsvReader<CsvRecord> reader = CsvReader.builder()
                .fieldSeparator(',')
                .commentStrategy(CommentStrategy.SKIP)
                .ofCsvRecord(readable)) {

            return reader.stream()
//                    .skip(1)
                    .filter(e -> e.getStartingLineNumber() != 1)
                    .map(this::mapEntry).toList();
        } catch (IOException e) {
            throw new RuntimeException("failed reading csv file", e);
        }
    }
}
