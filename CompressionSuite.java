import java.util.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import javax.swing.*;



public class CompressionSuite {


    //   HUFFMAN CODING


    static class Node implements Comparable<Node> {
        char ch; int freq;
        Node left, right;
        Node(char ch, int freq) { this.ch = ch; this.freq = freq; }
        public int compareTo(Node o) { return this.freq - o.freq; }
    }

    static Node huffmanRoot;
    static HashMap<Character, String> codeMap = new HashMap<>();

    static void buildHuffman(String text) {
        HashMap<Character, Integer> freq = new HashMap<>();
        for (char c : text.toCharArray())
            freq.put(c, freq.getOrDefault(c, 0) + 1);

        PriorityQueue<Node> pq = new PriorityQueue<>();
        for (char c : freq.keySet()) pq.add(new Node(c, freq.get(c)));

        // Edge case: only 1 unique character
        if (pq.size() == 1) {
            Node only = pq.poll();
            huffmanRoot = new Node('\0', only.freq);
            huffmanRoot.left = only;
            codeMap.clear();
            codeMap.put(only.ch, "0");
            return;
        }

        while (pq.size() > 1) {
            Node l = pq.poll(), r = pq.poll();
            Node p = new Node('\0', l.freq + r.freq);
            p.left = l; p.right = r;
            pq.add(p);
        }
        huffmanRoot = pq.poll();
        codeMap.clear();
        generateCodes(huffmanRoot, "");
    }

    static void generateCodes(Node node, String code) {
        if (node == null) return;
        if (node.left == null && node.right == null) {
            codeMap.put(node.ch, code.isEmpty() ? "0" : code);
            return;
        }
        generateCodes(node.left,  code + "0");
        generateCodes(node.right, code + "1");
    }

    static String huffmanEncode(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) sb.append(codeMap.get(c));
        return sb.toString();
    }

    static String huffmanDecode(String bits, HashMap<Character, String> savedCodes) {
        // Rebuild reverse map: binary code -> character
        HashMap<String, Character> reverseMap = new HashMap<>();
        for (Map.Entry<Character, String> e : savedCodes.entrySet())
            reverseMap.put(e.getValue(), e.getKey());

        StringBuilder result = new StringBuilder();
        StringBuilder cur    = new StringBuilder();
        for (char bit : bits.toCharArray()) {
            cur.append(bit);
            if (reverseMap.containsKey(cur.toString())) {
                result.append(reverseMap.get(cur.toString()));
                cur.setLength(0);
            }
        }
        return result.toString();
    }


    //   LZW COMPRESSION


    // FIX #4: handles single character input properly
    static int[] lzwCompress(String text) {
        HashMap<String, Integer> dict = new HashMap<>();
        for (int i = 0; i < 256; i++) dict.put(String.valueOf((char) i), i);

        ArrayList<Integer> output = new ArrayList<>();
        int nextCode = 256;

        if (text.length() == 1) {
            output.add((int) text.charAt(0));
            return output.stream().mapToInt(i -> i).toArray();
        }

        String cur = String.valueOf(text.charAt(0));
        for (int i = 1; i < text.length(); i++) {
            String next = cur + text.charAt(i);
            if (dict.containsKey(next)) {
                cur = next;
            } else {
                output.add(dict.get(cur));
                dict.put(next, nextCode++);
                cur = String.valueOf(text.charAt(i));
            }
        }
        output.add(dict.get(cur));
        return output.stream().mapToInt(i -> i).toArray();
    }

    static String lzwDecompress(int[] codes) {
        HashMap<Integer, String> dict = new HashMap<>();
        for (int i = 0; i < 256; i++) dict.put(i, String.valueOf((char) i));

        if (codes.length == 1) return String.valueOf((char) codes[0]);

        StringBuilder result = new StringBuilder();
        int nextCode = 256;
        String prev = dict.get(codes[0]);
        result.append(prev);

        for (int i = 1; i < codes.length; i++) {
            String entry = dict.containsKey(codes[i]) ? dict.get(codes[i]) : prev + prev.charAt(0);
            result.append(entry);
            dict.put(nextCode++, prev + entry.charAt(0));
            prev = entry;
        }
        return result.toString();
    }


    //   FIX #2: READ FILE AS BYTES (any file type)


    // Read any file (text, image, PDF, binary) as a char string via bytes
    static String readFileAsString(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        // Convert each byte to a char (0-255), preserving all data
        StringBuilder sb = new StringBuilder(bytes.length);
        for (byte b : bytes) sb.append((char)(b & 0xFF));
        return sb.toString();
    }

    // Write string back as bytes (for binary files)
    static void writeStringAsBytes(File file, String content) throws IOException {
        byte[] bytes = new byte[content.length()];
        for (int i = 0; i < content.length(); i++)
            bytes[i] = (byte)(content.charAt(i) & 0xFF);
        Files.write(file.toPath(), bytes);
    }

    static String fileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024));
    }


    //   FIX #3: SAVE CODE TABLE FOR DECOMPRESSION


    // Save: ALGO header + code table + encoded data
    static void saveHuffmanFile(File file, String encoded, HashMap<Character, String> codes) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("ALGO:HUFFMAN\n");
        // Save code table so decompression works without the tree
        sb.append("CODETABLE_START\n");
        for (Map.Entry<Character, String> e : codes.entrySet())
            sb.append((int) e.getKey()).append(":").append(e.getValue()).append("\n");
        sb.append("CODETABLE_END\n");
        sb.append(encoded);
        Files.write(file.toPath(), sb.toString().getBytes());
    }

    // Load and decompress a saved Huffman file
    static String loadAndHuffmanDecompress(File file) throws IOException {
        String content = new String(Files.readAllBytes(file.toPath()));
        String[] lines = content.split("\n");

        HashMap<Character, String> savedCodes = new HashMap<>();
        boolean inTable = false;
        int dataLineIndex = 0;

        for (int i = 0; i < lines.length; i++) {
            if (lines[i].equals("CODETABLE_START")) { inTable = true; continue; }
            if (lines[i].equals("CODETABLE_END"))   { inTable = false; dataLineIndex = i + 1; continue; }
            if (inTable) {
                String[] parts = lines[i].split(":");
                if (parts.length == 2)
                    savedCodes.put((char) Integer.parseInt(parts[0]), parts[1]);
            }
        }

        // Rest is encoded bits
        StringBuilder encodedBits = new StringBuilder();
        for (int i = dataLineIndex; i < lines.length; i++)
            encodedBits.append(lines[i]);

        return huffmanDecode(encodedBits.toString(), savedCodes);
    }

    static void saveLZWFile(File file, int[] codes) throws IOException {
        StringBuilder sb = new StringBuilder("ALGO:LZW\n");
        for (int c : codes) sb.append(c).append(" ");
        Files.write(file.toPath(), sb.toString().getBytes());
    }

    static String loadAndLZWDecompress(File file) throws IOException {
        String content = new String(Files.readAllBytes(file.toPath()));
        String dataLine = content.replace("ALGO:LZW\n", "").trim();
        String[] parts  = dataLine.split(" ");
        int[] codes = new int[parts.length];
        for (int i = 0; i < parts.length; i++) codes[i] = Integer.parseInt(parts[i]);
        return lzwDecompress(codes);
    }


    //   SAVED STATE

    static String                   lastEncoded  = "";
    static int[]                    lastLZWCodes = null;
    static String                   lastAlgo     = "";
    static HashMap<Character,String> lastCodeMap  = new HashMap<>();


    //   GUI


    public static void main(String[] args) {

        JFrame frame = new JFrame("Advanced File Compression Suite — M Ahmad");
        frame.setSize(860, 740);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(10, 10));
        frame.getContentPane().setBackground(new Color(30, 30, 46));

        // Title
        JLabel title = new JLabel("Advanced File Compression Suite", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 20));
        title.setForeground(Color.WHITE);
        title.setOpaque(true);
        title.setBackground(new Color(31, 78, 121));
        title.setBorder(BorderFactory.createEmptyBorder(14, 10, 14, 10));
        frame.add(title, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBackground(new Color(30, 30, 46));
        center.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        // File Picker Row
        JPanel fileRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        fileRow.setBackground(new Color(30, 30, 46));
        fileRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        fileRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JLabel fileLabel = new JLabel("Selected File:");
        fileLabel.setForeground(new Color(180, 210, 255));
        fileLabel.setFont(new Font("Arial", Font.BOLD, 13));

        JTextField filePathField = new JTextField("No file selected", 38);
        filePathField.setEditable(false);
        filePathField.setBackground(new Color(50, 50, 70));
        filePathField.setForeground(new Color(200, 200, 200));
        filePathField.setFont(new Font("Monospaced", Font.PLAIN, 12));
        filePathField.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JButton browseBtn = makeButton("Browse File", new Color(80, 80, 130));

        fileRow.add(fileLabel);
        fileRow.add(filePathField);
        fileRow.add(browseBtn);
        center.add(fileRow);
        center.add(Box.createVerticalStrut(8));

        JLabel orLabel = new JLabel("─────────── OR type text directly below ───────────");
        orLabel.setForeground(new Color(120, 120, 160));
        orLabel.setFont(new Font("Arial", Font.ITALIC, 12));
        orLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(orLabel);
        center.add(Box.createVerticalStrut(6));

        // Input area
        JTextArea inputArea = new JTextArea(4, 50);
        inputArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        inputArea.setBackground(new Color(50, 50, 70));
        inputArea.setForeground(Color.WHITE);
        inputArea.setCaretColor(Color.WHITE);
        inputArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        inputArea.setLineWrap(true);
        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        inputScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 95));
        center.add(inputScroll);
        center.add(Box.createVerticalStrut(10));

        // ── Button Row 1: Compress ──
        JLabel compressLabel = new JLabel("  COMPRESS:");
        compressLabel.setForeground(new Color(180, 210, 255));
        compressLabel.setFont(new Font("Arial", Font.BOLD, 12));
        compressLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(compressLabel);
        center.add(Box.createVerticalStrut(4));

        JPanel btnRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btnRow1.setBackground(new Color(30, 30, 46));
        btnRow1.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton huffCompBtn      = makeButton("Huffman Only",      new Color(46, 117, 182));
        JButton lzwCompBtn       = makeButton("LZW Only",          new Color(0, 130, 100));
        // FIX #1: Combined pipeline button
        JButton pipelineCompBtn  = makeButton("LZW + Huffman (Pipeline)", new Color(140, 60, 180));

        btnRow1.add(huffCompBtn);
        btnRow1.add(lzwCompBtn);
        btnRow1.add(pipelineCompBtn);
        center.add(btnRow1);
        center.add(Box.createVerticalStrut(8));

        // ── Button Row 2: Decompress ──
        JLabel decompressLabel = new JLabel("  DECOMPRESS:");
        decompressLabel.setForeground(new Color(180, 210, 255));
        decompressLabel.setFont(new Font("Arial", Font.BOLD, 12));
        decompressLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(decompressLabel);
        center.add(Box.createVerticalStrut(4));

        JPanel btnRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btnRow2.setBackground(new Color(30, 30, 46));
        btnRow2.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton huffDecompBtn     = makeButton("Huffman Decompress",       new Color(0, 100, 160));
        JButton lzwDecompBtn      = makeButton("LZW Decompress",           new Color(0, 100, 80));
        JButton pipelineDecompBtn = makeButton("Pipeline Decompress",      new Color(100, 40, 140));
        JButton loadDecompBtn     = makeButton("Load .huf/.lzw File",      new Color(100, 70, 20));

        btnRow2.add(huffDecompBtn);
        btnRow2.add(lzwDecompBtn);
        btnRow2.add(pipelineDecompBtn);
        btnRow2.add(loadDecompBtn);
        center.add(btnRow2);
        center.add(Box.createVerticalStrut(8));

        // ── Button Row 3: Save / Clear ──
        JPanel btnRow3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btnRow3.setBackground(new Color(30, 30, 46));
        btnRow3.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton saveBtn  = makeButton("Save Compressed File", new Color(130, 80, 0));
        JButton clearBtn = makeButton("Clear All",            new Color(150, 50, 50));

        btnRow3.add(saveBtn);
        btnRow3.add(clearBtn);
        center.add(btnRow3);
        center.add(Box.createVerticalStrut(10));

        // Output
        JLabel outputLabel = new JLabel("Output:");
        outputLabel.setForeground(new Color(180, 210, 255));
        outputLabel.setFont(new Font("Arial", Font.BOLD, 13));
        outputLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(outputLabel);
        center.add(Box.createVerticalStrut(6));

        JTextArea outputArea = new JTextArea(14, 50);
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        outputArea.setBackground(new Color(20, 20, 35));
        outputArea.setForeground(new Color(100, 230, 150));
        outputArea.setEditable(false);
        outputArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        outputArea.setLineWrap(true);
        JScrollPane outputScroll = new JScrollPane(outputArea);
        outputScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(outputScroll);

        frame.add(center, BorderLayout.CENTER);

        JLabel footer = new JLabel("M Ahmad  |  L1F24BSSE0345  |  University of the Punjab", SwingConstants.CENTER);
        footer.setForeground(new Color(120, 120, 160));
        footer.setFont(new Font("Arial", Font.PLAIN, 11));
        footer.setBorder(BorderFactory.createEmptyBorder(6, 0, 8, 0));
        frame.add(footer, BorderLayout.SOUTH);

        final File[] selectedFile = {null};

        // ── BROWSE ──
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select Any File to Compress");
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                selectedFile[0] = chooser.getSelectedFile();
                filePathField.setText(selectedFile[0].getAbsolutePath());
                try {
                    // FIX #2: read as bytes, works for any file type
                    String content = readFileAsString(selectedFile[0]);
                    inputArea.setText(content);
                    outputArea.setText(
                            "File loaded!\n" +
                                    "Name : " + selectedFile[0].getName() + "\n" +
                                    "Size : " + fileSize(selectedFile[0].length()) + "\n" +
                                    "Type : any binary/text file supported\n\n" +
                                    "Now click a Compress button."
                    );
                } catch (IOException ex) {
                    outputArea.setText("Error reading file: " + ex.getMessage());
                }
            }
        });

        // ── HUFFMAN COMPRESS ──
        huffCompBtn.addActionListener(e -> {
            String text = inputArea.getText();
            if (text.isEmpty()) { outputArea.setText("Enter text or load a file first!"); return; }
            try {
                buildHuffman(text);
                lastEncoded = huffmanEncode(text);
                lastAlgo    = "huffman";
                lastCodeMap = new HashMap<>(codeMap);
                int origBits  = text.length() * 8;
                double saved  = (1.0 - (double) lastEncoded.length() / origBits) * 100;

                outputArea.setText(
                        "======== HUFFMAN COMPRESS ========\n" +
                                "Original  : " + text.length() + " chars (" + origBits + " bits)\n" +
                                "Encoded   : " + lastEncoded.length() + " bits\n" +
                                "Saved     : " + String.format("%.1f", saved) + "%\n\n" +
                                "Codes     : " + codeMap + "\n\n" +
                                "Encoded (first 80): " + lastEncoded.substring(0, Math.min(80, lastEncoded.length())) + "...\n\n" +
                                "Click 'Save Compressed File' to save (code table included)."
                );
            } catch (Exception ex) { outputArea.setText("Error: " + ex.getMessage()); }
        });

        // ── LZW COMPRESS ──
        lzwCompBtn.addActionListener(e -> {
            String text = inputArea.getText();
            if (text.isEmpty()) { outputArea.setText("Enter text or load a file first!"); return; }
            try {
                lastLZWCodes = lzwCompress(text);
                lastAlgo     = "lzw";
                double saved = (1.0 - (double) lastLZWCodes.length / text.length()) * 100;

                outputArea.setText(
                        "======== LZW COMPRESS ========\n" +
                                "Original  : " + text.length() + " chars\n" +
                                "Codes     : " + lastLZWCodes.length + " codes\n" +
                                "Saved     : " + String.format("%.1f", saved) + "%\n\n" +
                                "First 20  : " + Arrays.toString(Arrays.copyOf(lastLZWCodes, Math.min(20, lastLZWCodes.length))) + " ...\n\n" +
                                "Click 'Save Compressed File' to save."
                );
            } catch (Exception ex) { outputArea.setText("Error: " + ex.getMessage()); }
        });

        // ── FIX #1: LZW + HUFFMAN PIPELINE ──
        pipelineCompBtn.addActionListener(e -> {
            String text = inputArea.getText();
            if (text.isEmpty()) { outputArea.setText("Enter text or load a file first!"); return; }
            try {
                // Step 1: LZW compress
                int[] lzwCodes = lzwCompress(text);

                // Convert LZW int codes to a string for Huffman input
                StringBuilder lzwStr = new StringBuilder();
                for (int code : lzwCodes) lzwStr.append((char)(code % 65536));
                String lzwAsString = lzwStr.toString();

                // Step 2: Huffman compress the LZW output
                buildHuffman(lzwAsString);
                lastEncoded = huffmanEncode(lzwAsString);
                lastAlgo    = "pipeline";
                lastCodeMap = new HashMap<>(codeMap);
                lastLZWCodes = lzwCodes;

                int origBits    = text.length() * 8;
                int lzwBits     = lzwCodes.length * 16;
                int finalBits   = lastEncoded.length();
                double savedLZW = (1.0 - (double) lzwBits / origBits) * 100;
                double savedAll = (1.0 - (double) finalBits / origBits) * 100;

                outputArea.setText(
                        "======== LZW + HUFFMAN PIPELINE ========\n\n" +
                                "STEP 1 — LZW Compress:\n" +
                                "  Original  : " + text.length() + " chars (" + origBits + " bits)\n" +
                                "  After LZW : " + lzwCodes.length + " codes (" + lzwBits + " bits)\n" +
                                "  Saved     : " + String.format("%.1f", savedLZW) + "%\n\n" +
                                "STEP 2 — Huffman Compress (on LZW output):\n" +
                                "  After LZW : " + lzwBits + " bits\n" +
                                "  Final     : " + finalBits + " bits\n" +
                                "  Saved     : " + String.format("%.1f", savedAll) + "% (total from original)\n\n" +
                                "Huffman Codes : " + codeMap + "\n\n" +
                                "Click 'Save Compressed File' to save."
                );
            } catch (Exception ex) { outputArea.setText("Pipeline Error: " + ex.getMessage()); }
        });

        // ── HUFFMAN DECOMPRESS ──
        huffDecompBtn.addActionListener(e -> {
            if (lastEncoded.isEmpty() || !lastAlgo.equals("huffman")) {
                outputArea.setText("Run 'Huffman Only' compress first!"); return;
            }
            try {
                String decoded = huffmanDecode(lastEncoded, lastCodeMap);
                outputArea.setText("======== HUFFMAN DECOMPRESS ========\n\n" + decoded);
            } catch (Exception ex) { outputArea.setText("Decompress Error: " + ex.getMessage()); }
        });

        // ── LZW DECOMPRESS ──
        lzwDecompBtn.addActionListener(e -> {
            if (lastLZWCodes == null || !lastAlgo.equals("lzw")) {
                outputArea.setText("Run 'LZW Only' compress first!"); return;
            }
            try {
                String result = lzwDecompress(lastLZWCodes);
                outputArea.setText("======== LZW DECOMPRESS ========\n\n" + result);
            } catch (Exception ex) { outputArea.setText("Decompress Error: " + ex.getMessage()); }
        });

        // ── PIPELINE DECOMPRESS ──
        pipelineDecompBtn.addActionListener(e -> {
            if (!lastAlgo.equals("pipeline")) {
                outputArea.setText("Run 'LZW + Huffman Pipeline' compress first!"); return;
            }
            try {
                // Step 1: Huffman decode
                String huffDecoded = huffmanDecode(lastEncoded, lastCodeMap);

                // Step 2: Convert back to int codes
                int[] codes = new int[huffDecoded.length()];
                for (int i = 0; i < huffDecoded.length(); i++)
                    codes[i] = huffDecoded.charAt(i);

                // Step 3: LZW decode
                String original = lzwDecompress(codes);
                outputArea.setText("======== PIPELINE DECOMPRESS ========\n\nStep 1: Huffman decoded\nStep 2: LZW decoded\n\nResult:\n" + original);
            } catch (Exception ex) { outputArea.setText("Pipeline Decompress Error: " + ex.getMessage()); }
        });

        // ── FIX #3: LOAD FILE AND DECOMPRESS ──
        loadDecompBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Open a .huf or .lzw compressed file");
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                File f = chooser.getSelectedFile();
                try {
                    String content = new String(Files.readAllBytes(f.toPath()));
                    if (content.startsWith("ALGO:HUFFMAN")) {
                        String result = loadAndHuffmanDecompress(f);
                        outputArea.setText("======== HUFFMAN FILE DECOMPRESS ========\n\n" + result);
                    } else if (content.startsWith("ALGO:LZW")) {
                        String result = loadAndLZWDecompress(f);
                        outputArea.setText("======== LZW FILE DECOMPRESS ========\n\n" + result);
                    } else {
                        outputArea.setText("Unknown file format! Only .huf or .lzw files supported.");
                    }
                } catch (Exception ex) { outputArea.setText("Load Error: " + ex.getMessage()); }
            }
        });

        // ── SAVE ──
        saveBtn.addActionListener(e -> {
            if (lastEncoded.isEmpty() && lastLZWCodes == null) {
                outputArea.setText("Compress something first!"); return;
            }
            JFileChooser chooser = new JFileChooser();
            String defaultName = lastAlgo.equals("lzw") ? "compressed.lzw" : "compressed.huf";
            chooser.setSelectedFile(new File(defaultName));
            if (chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
                File saveFile = chooser.getSelectedFile();
                try {
                    if (lastAlgo.equals("lzw")) {
                        saveLZWFile(saveFile, lastLZWCodes);
                    } else {
                        // FIX #3: saves code table too
                        saveHuffmanFile(saveFile, lastEncoded, lastCodeMap);
                    }
                    outputArea.setText(
                            "Saved successfully!\n" +
                                    "Path : " + saveFile.getAbsolutePath() + "\n" +
                                    "Size : " + fileSize(saveFile.length()) + "\n\n" +
                                    "(Code table included — decompression possible anytime)"
                    );
                } catch (IOException ex) { outputArea.setText("Save Error: " + ex.getMessage()); }
            }
        });

        // ── CLEAR ──
        clearBtn.addActionListener(e -> {
            inputArea.setText(""); outputArea.setText("");
            filePathField.setText("No file selected");
            selectedFile[0] = null;
            lastEncoded = ""; lastLZWCodes = null; lastAlgo = ""; lastCodeMap.clear();
        });

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    static JButton makeButton(String text, Color color) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Arial", Font.BOLD, 12));
        btn.setBackground(color);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(9, 14, 9, 14));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }
}