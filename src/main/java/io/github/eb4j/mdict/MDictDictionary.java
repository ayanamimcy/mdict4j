/*
 * MD4J, a parser library for MDict format.
 * Copyright (C) 2021 Hiroshi Miura.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.eb4j.mdict;

import io.github.eb4j.mdict.io.MDFileInputStream;
import io.github.eb4j.mdict.io.MDInputStream;
import io.github.eb4j.mdict.io.MDictUtils;
import org.bouncycastle.util.encoders.Hex;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.DataFormatException;

public class MDictDictionary {
    private final MDFileInputStream mdInputStream;
    private final DictionaryData<Object> dictionaryData;
    private final RecordIndex recordIndex;

    private final String title;
    private final Charset encoding;
    private final String creationDate;
    private final String format;
    private final String description;
    private final String styleSheet;
    private final int encrypted;
    private final boolean keyCaseSensitive;

    public MDictDictionary(final MDictDictionaryInfo info, final DictionaryData<Object> index,
                           final RecordIndex recordIndex, final MDFileInputStream mdInputStream) {
        dictionaryData = index;
        this.recordIndex = recordIndex;
        this.mdInputStream = mdInputStream;
        //
        title = info.getTitle();
        encoding = Charset.forName(info.getEncoding());
        creationDate = info.getCreationDate();
        format = info.getFormat();
        description = info.getDescription();
        styleSheet = info.getStyleSheet();
        encrypted = Integer.parseInt(info.getEncrypted());
        keyCaseSensitive = "true".equalsIgnoreCase(info.getKeyCaseSensitive());
    }

    public Charset getEncoding() {
        return encoding;
    }

    public String getTitle() {
        return title;
    }

    public String getCreationDate() {
        return creationDate;
    }

    public String getFormat() {
        return format;
    }

    public String getDescription() {
        return description;
    }

    public boolean isHeaderEncrypted() {
        return (encrypted & 0x01) > 0;
    }

    public boolean isIndexEncrypted() {
        return (encrypted & 0x02) > 0;
    }

    public boolean isKeyCaseSensitive() {
        return keyCaseSensitive;
    }

    public String getStyleSheet() {
        return styleSheet;
    }

    public List<Map.Entry<String, Object>> getEntries(final String word) {
        return dictionaryData.lookUp(word);
    }

    public List<Map.Entry<String, Object>> getEntriesPredictive(final String word) {
        return dictionaryData.lookUpPredictive(word);
    }

    public String getText(final Long offset) throws MDException {
        String result;
        // calculate block index and seek it
        int index = recordIndex.searchOffsetIndex(offset);
        long skipSize = offset - recordIndex.getRecordOffsetDecomp(index);
        try {
            mdInputStream.seek(recordIndex.getCompOffset(index));
        } catch (IOException e) {
            throw new MDException("IO error.", e);
        }
        long compSize = recordIndex.getRecordCompSize(index);
        long decompSize = recordIndex.getRecordDecompSize(index);
        try (MDInputStream decompressedStream = MDictUtils.decompress(mdInputStream, compSize, decompSize, false)) {
            long moved = decompressedStream.skip(skipSize);
            if (moved != skipSize) {
                throw new MDException("Decompressed data seems incorrect.");
            }
            try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(decompressedStream, encoding),
                    (int) decompSize)) {
                result = bufferedReader.readLine();
                return result;
            }
        } catch (DataFormatException | IOException e) {
            throw new MDException("data decompression error.", e);
        }
    }

    public static MDictDictionary loadDicitonary(final String mdxFile) throws MDException, IOException {
        MDictDictionaryInfo info;
        DictionaryData<Object> index;
        RecordIndex record;
        File file = new File(mdxFile);
        if (!file.isFile()) {
            throw new MDException("Target file is not MDict file.");
        }
        String f = mdxFile;
        if (f.endsWith(".mdx")) {
            f = f.substring(0, f.length() - ".mdx".length());
        }
        String dictName = f;
        File keyFile = new File( dictName + ".key");
        byte[] keyword = null;
        if (keyFile.canRead()) {
            try {
                keyword = loadDictionaryKey(keyFile);
            } catch (IOException ignored) {
                // ignore keyfile loading failed.
            }
        }
        MDFileInputStream mdInputStream;
        try {
            mdInputStream = new MDFileInputStream(mdxFile);
            MDictMDXParser parser = new MDictMDXParser(mdInputStream);
            info = parser.parseHeader();
            index = parser.parseIndex(keyword);
            record = parser.parseRecordBlock();
        } catch (IOException | DataFormatException e) {
            throw new MDException("Dictionary data read error", e);
        }
        File mddFile = new File(dictName + ".mdd");
        return new MDictDictionary(info, index, record, mdInputStream);
    }

    /**
     * parse dictionary.key file and return 128-bit regcode.
     * @param keyFile dictionary.key file.
     * @return byte[] password data
     * @throws IOException when file read failed.
     */
    private static byte[] loadDictionaryKey(final File keyFile) throws IOException {
        byte[] keydata = new byte[16];
        if (keyFile.isFile() && keyFile.canRead()) {
            try (Stream<String> lines = Files.lines(keyFile.toPath())) {
                String first = lines.findFirst().orElse(null);
                if (first != null) {
                    first = first.substring(0, 32);
                    byte[] temp = Hex.decode(first);
                    System.arraycopy(temp, 0, keydata, 0, 16);
                }
            }
        }
        return keydata;
    }
}
