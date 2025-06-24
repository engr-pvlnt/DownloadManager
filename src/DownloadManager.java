
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

public class DownloadManager extends JFrame {
    private DefaultTableModel tableModel;
    private JTable downloadTable;
    private JTextField urlField;
    private JTextField savePathField;
    private JButton addButton, pauseButton, resumeButton, removeButton, browseButton, aboutButton;
    private List<DownloadTask> downloads;
    private ExecutorService executor;
    private static final String DOWNLOADS_FILE = "downloads_history.properties";
    private Properties downloadHistory;
    private JPopupMenu contextMenu;
    private Pattern urlPattern;
    private JButton addMultipleButton, scheduleButton;
    private JCheckBox scheduleCheckBox;
    private JSpinner dateSpinner, timeSpinner;
    private JPanel schedulePanel;
    private ScheduledExecutorService scheduledExecutor;
    private static final String APP_NAME = "Fast Download Manager";
    private static final String APP_VERSION = "2.0.1";
    private static final String APP_AUTHOR = "PBVLNT (p.velante@gmail.com)";
    private static final String APP_COPYRIGHT = "© 2025. ZanCed Software Solutions. All rights reserved.";

    public DownloadManager() {
        downloads = new ArrayList<>();
        executor = Executors.newFixedThreadPool(10);
        downloadHistory = new Properties();
        urlPattern = Pattern.compile(
                "^(https?|ftp)://[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+$",
                Pattern.CASE_INSENSITIVE);
        loadDownloadHistory();
        initializeGUI();
        setupContextMenu();
        setupClipboardMonitoring();
    }

    private void initializeGUI() {
        setTitle("Fast Download Manager");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        scheduledExecutor = new ScheduledThreadPoolExecutor(5);

        // Top panel for URL input
        JPanel topPanel = new JPanel(new BorderLayout());
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(5, 5, 5, 5);
        inputPanel.add(new JLabel("URL:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        urlField = new JTextField();
        inputPanel.add(urlField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        inputPanel.add(new JLabel("Save to:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        savePathField = new JTextField(System.getProperty("user.home") + "/Downloads");
        inputPanel.add(savePathField, gbc);

        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        browseButton = new JButton("Browse");
        inputPanel.add(browseButton, gbc);

        // Schedule panel
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        schedulePanel = createSchedulePanel();
        inputPanel.add(schedulePanel, gbc);

        topPanel.add(inputPanel, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        addButton = new JButton("Add Download");
        addMultipleButton = new JButton("Add Multiple URLs");
        scheduleButton = new JButton("Schedule Download");
        pauseButton = new JButton("Pause");
        resumeButton = new JButton("Resume");
        removeButton = new JButton("Remove");
        aboutButton = new JButton("About");

        buttonPanel.add(addButton);
        buttonPanel.add(addMultipleButton);
        buttonPanel.add(scheduleButton);
        buttonPanel.add(pauseButton);
        buttonPanel.add(resumeButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(aboutButton);

        topPanel.add(buttonPanel, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        // Download table with elapsed time column
        String[] columns = { "File Name", "URL", "Size", "Progress", "Speed", "Status", "Elapsed Time", "Date" };
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        downloadTable = new JTable(tableModel);
        downloadTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scrollPane = new JScrollPane(downloadTable);
        add(scrollPane, BorderLayout.CENTER);

        // Load previous downloads
        loadPreviousDownloads();

        // Event listeners
        addButton.addActionListener(e -> addDownload());
        addMultipleButton.addActionListener(e -> addMultipleDownloads());
        scheduleButton.addActionListener(e -> scheduleDownload());
        pauseButton.addActionListener(e -> pauseDownload());
        resumeButton.addActionListener(e -> resumeDownload());
        removeButton.addActionListener(e -> removeDownload());
        browseButton.addActionListener(e -> browseDirectory());
        aboutButton.addActionListener(e -> showAboutDialog());

        // URL field focus listener for clipboard detection
        urlField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                checkClipboardForURL();
            }
        });

        setSize(1000, 700);
        setLocationRelativeTo(null);
    }

    private void setupContextMenu() {
        contextMenu = new JPopupMenu();

        JMenuItem openFile = new JMenuItem("Open File");
        JMenuItem locateFile = new JMenuItem("Show in Folder");
        JMenuItem deleteFile = new JMenuItem("Delete File");
        JMenuItem copyURL = new JMenuItem("Copy URL");
        JMenuItem redownload = new JMenuItem("Re-download");

        openFile.addActionListener(e -> openSelectedFile());
        locateFile.addActionListener(e -> locateSelectedFile());
        deleteFile.addActionListener(e -> deleteSelectedFile());
        copyURL.addActionListener(e -> copySelectedURL());
        redownload.addActionListener(e -> redownloadSelected());

        contextMenu.add(openFile);
        contextMenu.add(locateFile);
        contextMenu.addSeparator();
        contextMenu.add(deleteFile);
        contextMenu.addSeparator();
        contextMenu.add(copyURL);
        contextMenu.add(redownload);

        downloadTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = downloadTable.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        downloadTable.setRowSelectionInterval(row, row);
                        contextMenu.show(downloadTable, e.getX(), e.getY());
                    }
                }
            }
        });
    }

    private JPanel createSchedulePanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        scheduleCheckBox = new JCheckBox("Schedule Download");
        panel.add(scheduleCheckBox);

        panel.add(new JLabel("Date:"));

        // Date spinner
        SpinnerDateModel dateModel = new SpinnerDateModel();
        dateSpinner = new JSpinner(dateModel);
        JSpinner.DateEditor dateEditor = new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd");
        dateSpinner.setEditor(dateEditor);
        dateSpinner.setPreferredSize(new Dimension(100, 25));
        panel.add(dateSpinner);

        panel.add(new JLabel("Time:"));

        // Time spinner
        SpinnerDateModel timeModel = new SpinnerDateModel();
        timeSpinner = new JSpinner(timeModel);
        JSpinner.DateEditor timeEditor = new JSpinner.DateEditor(timeSpinner, "HH:mm");
        timeSpinner.setEditor(timeEditor);
        timeSpinner.setPreferredSize(new Dimension(60, 25));
        panel.add(timeSpinner);

        // Initially hide the schedule controls
        dateSpinner.setVisible(false);
        timeSpinner.setVisible(false);
        panel.getComponent(1).setVisible(false); // "Date:" label
        panel.getComponent(3).setVisible(false); // "Time:" label

        scheduleCheckBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                boolean selected = e.getStateChange() == ItemEvent.SELECTED;
                dateSpinner.setVisible(selected);
                timeSpinner.setVisible(selected);
                panel.getComponent(1).setVisible(selected); // "Date:" label
                panel.getComponent(3).setVisible(selected); // "Time:" label
                panel.revalidate();
                panel.repaint();
            }
        });

        return panel;
    }

    private void showAboutDialog() {
        JDialog aboutDialog = new JDialog(this, "About " + APP_NAME, true);
        aboutDialog.setLayout(new BorderLayout());
        aboutDialog.setSize(450, 500);
        aboutDialog.setLocationRelativeTo(this);
        aboutDialog.setResizable(false);

        // Create main panel with padding
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Create info panel
        JPanel infoPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 0, 10, 0);
        gbc.anchor = GridBagConstraints.CENTER;

        // Application icon (you can replace with actual icon)
        JLabel iconLabel = new JLabel("⬇");
        iconLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 48));
        iconLabel.setForeground(new Color(0, 122, 255));
        gbc.gridx = 0;
        gbc.gridy = 0;
        infoPanel.add(iconLabel, gbc);

        // Application name
        JLabel nameLabel = new JLabel(APP_NAME);
        nameLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        gbc.gridy = 1;
        infoPanel.add(nameLabel, gbc);

        // Version
        JLabel versionLabel = new JLabel("Version " + APP_VERSION);
        versionLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        versionLabel.setForeground(Color.GRAY);
        gbc.gridy = 2;
        infoPanel.add(versionLabel, gbc);

        // Author
        JLabel authorLabel = new JLabel("Developed by " + APP_AUTHOR);
        authorLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        gbc.gridy = 3;
        gbc.insets = new Insets(20, 0, 5, 0);
        infoPanel.add(authorLabel, gbc);

        // Copyright
        JLabel copyrightLabel = new JLabel(APP_COPYRIGHT);
        copyrightLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        copyrightLabel.setForeground(Color.GRAY);
        gbc.gridy = 4;
        gbc.insets = new Insets(5, 0, 10, 0);
        infoPanel.add(copyrightLabel, gbc);

        // Description
        JTextArea descriptionArea = new JTextArea(
                "A fast and reliable download manager with support for\n" +
                        "multi-threaded downloads, scheduling, and clipboard monitoring.\n\n" +
                        "Features:\n" +
                        "• Multi-threaded downloading\n" +
                        "• Download scheduling\n" +
                        "• Clipboard URL detection\n" +
                        "• Download history\n" +
                        "• Pause and resume functionality");
        descriptionArea.setEditable(false);
        descriptionArea.setOpaque(false);
        descriptionArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        descriptionArea.setForeground(Color.DARK_GRAY);
        gbc.gridy = 5;
        gbc.insets = new Insets(10, 0, 20, 0);
        infoPanel.add(descriptionArea, gbc);

        mainPanel.add(infoPanel, BorderLayout.CENTER);

        // Create button panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton okButton = new JButton("OK");
        okButton.setPreferredSize(new Dimension(80, 30));
        okButton.addActionListener(e -> aboutDialog.dispose());
        buttonPanel.add(okButton);

        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        aboutDialog.add(mainPanel);

        // Set focus to OK button
        SwingUtilities.invokeLater(() -> okButton.requestFocus());

        aboutDialog.setVisible(true);
    }

    private void addMultipleDownloads() {
        JTextArea urlArea = new JTextArea(10, 50);
        urlArea.setLineWrap(true);
        urlArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(urlArea);

        String message = "Enter multiple URLs (one per line):";

        int result = JOptionPane.showConfirmDialog(this, scrollPane, message,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String[] urls = urlArea.getText().split("\n");
            int addedCount = 0;

            for (String url : urls) {
                url = url.trim();
                if (!url.isEmpty() && isValidURL(url)) {
                    try {
                        addSingleDownload(url);
                        addedCount++;
                    } catch (Exception e) {
                        System.err.println("Error adding URL: " + url + " - " + e.getMessage());
                    }
                }
            }

            if (addedCount > 0) {
                JOptionPane.showMessageDialog(this,
                        "Successfully added " + addedCount + " downloads out of " + urls.length + " URLs.");
            } else {
                JOptionPane.showMessageDialog(this, "No valid URLs were found.");
            }
        }
    }

    private void scheduleDownload() {
        String url = urlField.getText().trim();
        String savePath = savePathField.getText().trim();

        if (url.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a URL");
            return;
        }

        if (!isValidURL(url)) {
            JOptionPane.showMessageDialog(this, "Please enter a valid URL");
            return;
        }

        if (!scheduleCheckBox.isSelected()) {
            JOptionPane.showMessageDialog(this, "Please check 'Schedule Download' and set date/time");
            return;
        }

        try {
            // Get scheduled date and time
            java.util.Date scheduledDate = (java.util.Date) dateSpinner.getValue();
            java.util.Date scheduledTime = (java.util.Date) timeSpinner.getValue();

            // Combine date and time
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(scheduledDate);

            java.util.Calendar timeCal = java.util.Calendar.getInstance();
            timeCal.setTime(scheduledTime);

            cal.set(java.util.Calendar.HOUR_OF_DAY, timeCal.get(java.util.Calendar.HOUR_OF_DAY));
            cal.set(java.util.Calendar.MINUTE, timeCal.get(java.util.Calendar.MINUTE));
            cal.set(java.util.Calendar.SECOND, 0);

            long delay = cal.getTimeInMillis() - System.currentTimeMillis();

            if (delay <= 0) {
                JOptionPane.showMessageDialog(this, "Scheduled time must be in the future!");
                return;
            }

            // Add to table as scheduled
            DownloadTask task = new DownloadTask(url, savePath, this);
            downloads.add(task);

            String fileName = task.getFileName();
            String currentDate = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
            String scheduledDateStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(cal.getTime());

            Object[] rowData = { fileName, url, "Calculating...", "0%", "0 KB/s",
                    "Scheduled for " + scheduledDateStr, "00:00:00", currentDate };
            tableModel.addRow(rowData);

            task.setRowIndex(tableModel.getRowCount() - 1);

            // Schedule the download
            scheduledExecutor.schedule(() -> {
                SwingUtilities.invokeLater(() -> {
                    updateDownloadProgress(task.getRowIndex(), "Calculating...", "0%", "0 KB/s", "Starting");
                });
                executor.submit(task);
            }, delay, TimeUnit.MILLISECONDS);

            // Save to history
            downloadHistory.setProperty(url, fileName + "|" + savePath + "|" + currentDate + "|Scheduled");
            saveDownloadHistory();

            urlField.setText("");
            scheduleCheckBox.setSelected(false);

            JOptionPane.showMessageDialog(this,
                    "Download scheduled for " + scheduledDateStr);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error scheduling download: " + e.getMessage());
        }
    }

    private void setupClipboardMonitoring() {
        // Timer to periodically check clipboard when URL field has focus
        javax.swing.Timer clipboardTimer = new javax.swing.Timer(1000, e -> {
            if (urlField.hasFocus() && urlField.getText().trim().isEmpty()) {
                checkClipboardForURL();
            }
        });
        clipboardTimer.setRepeats(true);
        clipboardTimer.start();
    }

    private void checkClipboardForURL() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                String clipboardText = (String) clipboard.getData(DataFlavor.stringFlavor);
                if (clipboardText != null && isValidURL(clipboardText.trim())) {
                    if (urlField.getText().trim().isEmpty()) {
                        urlField.setText(clipboardText.trim());
                        urlField.setBackground(new Color(230, 255, 230)); // Light green hint

                        // Reset background after 2 seconds
                        javax.swing.Timer timer = new javax.swing.Timer(2000, e -> urlField.setBackground(Color.WHITE));
                        timer.setRepeats(false);
                        timer.start();
                    }
                }
            }
        } catch (Exception e) {
            // Ignore clipboard access errors
        }
    }

    private boolean isValidURL(String url) {
        return urlPattern.matcher(url).matches();
    }

    private void browseDirectory() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setCurrentDirectory(new File(savePathField.getText()));

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            savePathField.setText(fileChooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void openSelectedFile() {
        int selectedRow = downloadTable.getSelectedRow();
        if (selectedRow >= 0) {
            String fileName = (String) tableModel.getValueAt(selectedRow, 0);
            String status = (String) tableModel.getValueAt(selectedRow, 5);

            if ("Completed".equals(status)) {
                try {
                    File file = new File(savePathField.getText(), fileName);
                    if (file.exists()) {
                        Desktop.getDesktop().open(file);
                    } else {
                        JOptionPane.showMessageDialog(this, "File not found: " + file.getAbsolutePath());
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this, "Cannot open file: " + e.getMessage());
                }
            } else {
                JOptionPane.showMessageDialog(this, "File is not completed yet.");
            }
        }
    }

    private void locateSelectedFile() {
        int selectedRow = downloadTable.getSelectedRow();
        if (selectedRow >= 0) {
            String fileName = (String) tableModel.getValueAt(selectedRow, 0);
            String status = (String) tableModel.getValueAt(selectedRow, 5);

            if ("Completed".equals(status)) {
                try {
                    File file = new File(savePathField.getText(), fileName);
                    if (file.exists()) {
                        if (System.getProperty("os.name").toLowerCase().contains("win")) {
                            new ProcessBuilder("explorer.exe", "/select,", file.getAbsolutePath()).start();
                        } else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                            new ProcessBuilder("open", "-R", file.getAbsolutePath()).start();
                        } else {
                            Desktop.getDesktop().open(file.getParentFile());
                        }
                    } else {
                        JOptionPane.showMessageDialog(this, "File not found: " + file.getAbsolutePath());
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this, "Cannot locate file: " + e.getMessage());
                }
            } else {
                JOptionPane.showMessageDialog(this, "File is not completed yet.");
            }
        }
    }

    private void deleteSelectedFile() {
        int selectedRow = downloadTable.getSelectedRow();
        if (selectedRow >= 0) {
            String fileName = (String) tableModel.getValueAt(selectedRow, 0);
            String url = (String) tableModel.getValueAt(selectedRow, 1);

            int confirm = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to delete the file and remove from history?\nFile: " + fileName,
                    "Confirm Delete", JOptionPane.YES_NO_OPTION);

            if (confirm == JOptionPane.YES_OPTION) {
                try {
                    File file = new File(savePathField.getText(), fileName);
                    if (file.exists()) {
                        file.delete();
                    }

                    // Remove from history
                    downloadHistory.remove(url);
                    saveDownloadHistory();

                    // Remove from table and list
                    if (selectedRow < downloads.size()) {
                        downloads.get(selectedRow).cancel();
                        downloads.remove(selectedRow);
                    }
                    tableModel.removeRow(selectedRow);

                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this, "Error deleting file: " + e.getMessage());
                }
            }
        }
    }

    private void copySelectedURL() {
        int selectedRow = downloadTable.getSelectedRow();
        if (selectedRow >= 0) {
            String url = (String) tableModel.getValueAt(selectedRow, 1);
            StringSelection selection = new StringSelection(url);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            JOptionPane.showMessageDialog(this, "URL copied to clipboard!");
        }
    }

    private void redownloadSelected() {
        int selectedRow = downloadTable.getSelectedRow();
        if (selectedRow >= 0) {
            String url = (String) tableModel.getValueAt(selectedRow, 1);
            urlField.setText(url);
            addDownload();
        }
    }

    private void addDownload() {
        String url = urlField.getText().trim();

        if (url.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a URL");
            return;
        }

        if (!isValidURL(url)) {
            JOptionPane.showMessageDialog(this, "Please enter a valid URL");
            return;
        }

        try {
            addSingleDownload(url);
            urlField.setText("");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error adding download: " + e.getMessage());
        }
    }

    private void addSingleDownload(String url) throws Exception {
        String savePath = savePathField.getText().trim();

        // Validate and create save directory
        File saveDir = new File(savePath);
        if (!saveDir.exists()) {
            boolean created = saveDir.mkdirs();
            if (!created) {
                throw new Exception("Cannot create save directory: " + savePath);
            }
        }

        if (!saveDir.isDirectory()) {
            throw new Exception("Save path is not a directory: " + savePath);
        }

        DownloadTask task = new DownloadTask(url, savePath, this);
        downloads.add(task);

        String fileName = task.getFileName();
        String currentDate = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
        Object[] rowData = { fileName, url, "Calculating...", "0%", "0 KB/s", "Starting", "00:00:00", currentDate };
        tableModel.addRow(rowData);

        task.setRowIndex(tableModel.getRowCount() - 1);
        executor.submit(task);

        // Save to history
        downloadHistory.setProperty(url, fileName + "|" + savePath + "|" + currentDate + "|Starting");
        saveDownloadHistory();
    }

    private void pauseDownload() {
        int selectedRow = downloadTable.getSelectedRow();
        if (selectedRow >= 0 && selectedRow < downloads.size()) {
            downloads.get(selectedRow).pause();
        }
    }

    private void resumeDownload() {
        int selectedRow = downloadTable.getSelectedRow();
        if (selectedRow >= 0 && selectedRow < downloads.size()) {
            DownloadTask task = downloads.get(selectedRow);
            if (task.isPaused()) {
                executor.submit(task);
            }
        }
    }

    private void removeDownload() {
        int selectedRow = downloadTable.getSelectedRow();
        if (selectedRow >= 0) {
            String url = (String) tableModel.getValueAt(selectedRow, 1);

            int confirm = JOptionPane.showConfirmDialog(this,
                    "Remove this download from the list?",
                    "Confirm Remove", JOptionPane.YES_NO_OPTION);

            if (confirm == JOptionPane.YES_OPTION) {
                if (selectedRow < downloads.size()) {
                    downloads.get(selectedRow).cancel();
                    downloads.remove(selectedRow);
                }

                // Remove from history
                downloadHistory.remove(url);
                saveDownloadHistory();

                tableModel.removeRow(selectedRow);
            }
        }
    }

    private void loadDownloadHistory() {
        try {
            File historyFile = new File(DOWNLOADS_FILE);
            if (historyFile.exists()) {
                try (FileInputStream fis = new FileInputStream(historyFile)) {
                    downloadHistory.load(fis);
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading download history: " + e.getMessage());
        }
    }

    private void saveDownloadHistory() {
        try {
            try (FileOutputStream fos = new FileOutputStream(DOWNLOADS_FILE)) {
                downloadHistory.store(fos, "Download Manager History");
            }
        } catch (Exception e) {
            System.err.println("Error saving download history: " + e.getMessage());
        }
    }

    private void loadPreviousDownloads() {
        for (String url : downloadHistory.stringPropertyNames()) {
            String[] parts = downloadHistory.getProperty(url).split("\\|");
            if (parts.length >= 4) {
                String fileName = parts[0];
                String savePath = parts[1];
                String date = parts[2];
                String status = parts[3];

                // Check if file still exists
                File file = new File(savePath, fileName);
                if (file.exists() && "Completed".equals(status)) {
                    Object[] rowData = { fileName, url, formatFileSize(file.length()),
                            "100%", "", "Completed", "00:00:00", date };
                    tableModel.addRow(rowData);
                } else if (!"Completed".equals(status)) {
                    Object[] rowData = { fileName, url, "", "0%", "", status, "00:00:00", date };
                    tableModel.addRow(rowData);
                }
            }
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    public void updateDownloadProgress(int rowIndex, String size, String progress, String speed, String status) {
        SwingUtilities.invokeLater(() -> {
            if (rowIndex < tableModel.getRowCount()) {
                tableModel.setValueAt(size, rowIndex, 2);
                tableModel.setValueAt(progress, rowIndex, 3);
                tableModel.setValueAt(speed, rowIndex, 4);
                tableModel.setValueAt(status, rowIndex, 5);

                // Update history
                String url = (String) tableModel.getValueAt(rowIndex, 1);
                String fileName = (String) tableModel.getValueAt(rowIndex, 0);
                String date = (String) tableModel.getValueAt(rowIndex, 7);
                String savePath = savePathField.getText();

                downloadHistory.setProperty(url, fileName + "|" + savePath + "|" + date + "|" + status);
                if ("Completed".equals(status) || "Error".equals(status) || "Cancelled".equals(status)) {
                    saveDownloadHistory();
                }
            }
        });
    }

    public void updateElapsedTime(int rowIndex, String elapsedTime) {
        SwingUtilities.invokeLater(() -> {
            if (rowIndex < tableModel.getRowCount()) {
                tableModel.setValueAt(elapsedTime, rowIndex, 6);
            }
        });
    }

    @Override
    public void dispose() {
        saveDownloadHistory();
        executor.shutdown();
        if (scheduledExecutor != null) {
            scheduledExecutor.shutdown();
        }
        super.dispose();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new DownloadManager().setVisible(true);
        });
    }
}

class DownloadTask implements Runnable {
    private String url;
    private String savePath;
    private String fileName;
    private long fileSize;
    private boolean paused;
    private boolean cancelled;
    private DownloadManager parent;
    private int rowIndex;
    private long startTime;
    private Timer elapsedTimer;
    private static final int THREAD_COUNT = 8;
    private static final int BUFFER_SIZE = 8192;

    public DownloadTask(String url, String savePath, DownloadManager parent) {
        this.url = url;
        this.savePath = savePath;
        this.parent = parent;
        this.fileName = sanitizeFileName(getFileNameFromUrl(url));
        this.paused = false;
        this.cancelled = false;
    }

    public String getFileName() {
        return fileName;
    }

    @Override
    public void run() {
        try {
            if (paused || cancelled)
                return;

            startTime = System.currentTimeMillis();
            startElapsedTimer();

            // Get file information
            HttpURLConnection connection = (HttpURLConnection) java.net.URI.create(url).toURL().openConnection();
            connection.setRequestMethod("HEAD");

            fileSize = connection.getContentLengthLong();
            boolean supportsRanges = "bytes".equals(connection.getHeaderField("Accept-Ranges"));

            updateStatus("Downloading", formatFileSize(fileSize), "0%", "0 KB/s");

            File saveFile = new File(savePath, fileName);

            // Ensure the file path is valid and within the save directory
            String canonicalSavePath = new File(savePath).getCanonicalPath();
            String canonicalFilePath = saveFile.getCanonicalPath();

            if (!canonicalFilePath.startsWith(canonicalSavePath)) {
                throw new SecurityException("Invalid file path: potential directory traversal");
            }

            if (supportsRanges && fileSize > 1024 * 1024) { // Use multi-threading for files > 1MB
                downloadWithMultipleThreads(saveFile);
            } else {
                downloadWithSingleThread(saveFile);
            }

        } catch (Exception e) {
            updateStatus("Error: " + e.getMessage(), "", "", "");
            e.printStackTrace();
        } finally {
            stopElapsedTimer();
        }
    }

    private void startElapsedTimer() {
        elapsedTimer = new Timer();
        elapsedTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!cancelled && !paused) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    String elapsedStr = formatElapsedTime(elapsed);
                    parent.updateElapsedTime(rowIndex, elapsedStr);
                }
            }
        }, 1000, 1000); // Update every second
    }

    private void stopElapsedTimer() {
        if (elapsedTimer != null) {
            elapsedTimer.cancel();
            elapsedTimer = null;
        }
    }

    private String formatElapsedTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        seconds = seconds % 60;

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private void downloadWithMultipleThreads(File saveFile) throws Exception {
        long chunkSize = fileSize / THREAD_COUNT;
        List<Future<?>> futures = new ArrayList<>();
        ExecutorService downloadExecutor = Executors.newFixedThreadPool(THREAD_COUNT);

        // Create temporary files for each chunk
        File[] tempFiles = new File[THREAD_COUNT];
        for (int i = 0; i < THREAD_COUNT; i++) {
            tempFiles[i] = new File(saveFile.getParent(), saveFile.getName() + ".part" + i);
        }

        // Start download threads
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadIndex = i;
            long startPos = i * chunkSize;
            long endPos = (i == THREAD_COUNT - 1) ? fileSize - 1 : (startPos + chunkSize - 1);

            Future<?> future = downloadExecutor.submit(() -> {
                try {
                    downloadChunk(startPos, endPos, tempFiles[threadIndex]);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            futures.add(future);
        }

        // Monitor progress
        while (!allThreadsCompleted(futures) && !cancelled && !paused) {
            updateProgressFromTempFiles(tempFiles);
            Thread.sleep(500);
        }

        downloadExecutor.shutdown();

        if (!cancelled && !paused) {
            // Merge all chunks
            updateStatus("Merging files...", formatFileSize(fileSize), "100%", "");
            mergeFiles(tempFiles, saveFile);
            updateStatus("Completed", formatFileSize(fileSize), "100%", "");
        }
    }

    private void downloadChunk(long startPos, long endPos, File tempFile) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) java.net.URI.create(url).toURL().openConnection();
        connection.setRequestProperty("Range", "bytes=" + startPos + "-" + endPos);

        try (InputStream in = connection.getInputStream();
                FileOutputStream out = new FileOutputStream(tempFile)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1 && !cancelled && !paused) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }

    private void downloadWithSingleThread(File saveFile) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) java.net.URI.create(url).toURL().openConnection();

        try (InputStream in = connection.getInputStream();
                FileOutputStream out = new FileOutputStream(saveFile)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalBytesRead = 0;
            long lastUpdateTime = System.currentTimeMillis();

            while ((bytesRead = in.read(buffer)) != -1 && !cancelled && !paused) {
                out.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;

                if (System.currentTimeMillis() - lastUpdateTime > 500) {
                    double progress = (double) totalBytesRead / fileSize * 100;
                    long speed = calculateSpeed(totalBytesRead, startTime);
                    updateStatus("Downloading", formatFileSize(fileSize),
                            String.format("%.1f%%", progress), formatSpeed(speed));
                    lastUpdateTime = System.currentTimeMillis();
                }
            }

            if (!cancelled && !paused) {
                updateStatus("Completed", formatFileSize(fileSize), "100%", "");
            }
        }
    }

    private boolean allThreadsCompleted(List<Future<?>> futures) {
        return futures.stream().allMatch(Future::isDone);
    }

    private void updateProgressFromTempFiles(File[] tempFiles) {
        long totalDownloaded = 0;
        for (File tempFile : tempFiles) {
            if (tempFile.exists()) {
                totalDownloaded += tempFile.length();
            }
        }

        double progress = (double) totalDownloaded / fileSize * 100;
        long speed = calculateSpeed(totalDownloaded, startTime);
        updateStatus("Downloading", formatFileSize(fileSize),
                String.format("%.1f%%", progress), formatSpeed(speed));
    }

    private void mergeFiles(File[] tempFiles, File outputFile) throws Exception {
        try (FileOutputStream out = new FileOutputStream(outputFile)) {
            for (File tempFile : tempFiles) {
                if (tempFile.exists()) {
                    try (FileInputStream in = new FileInputStream(tempFile)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                    tempFile.delete();
                }
            }
        }
    }

    private long calculateSpeed(long bytesDownloaded, long startTime) {
        long elapsedTime = System.currentTimeMillis() - startTime;
        return elapsedTime > 0 ? (bytesDownloaded * 1000) / elapsedTime : 0;
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private String formatSpeed(long bytesPerSecond) {
        return formatFileSize(bytesPerSecond) + "/s";
    }

    private String getFileNameFromUrl(String url) {
        String fileName = url.substring(url.lastIndexOf('/') + 1);

        // Remove query parameters and fragments
        int queryIndex = fileName.indexOf('?');
        if (queryIndex != -1) {
            fileName = fileName.substring(0, queryIndex);
        }
        int fragmentIndex = fileName.indexOf('#');
        if (fragmentIndex != -1) {
            fileName = fileName.substring(0, fragmentIndex);
        }

        // Sanitize filename - remove invalid characters
        fileName = sanitizeFileName(fileName);

        if (fileName.isEmpty() || !fileName.contains(".")) {
            fileName = "download_" + System.currentTimeMillis() + ".tmp";
        }
        return fileName;
    }

    private String sanitizeFileName(String fileName) {
        // Remove or replace invalid characters for Windows/Linux/Mac
        String sanitized = fileName.replaceAll("[<>:\"/\\\\|?*]", "_");

        // Remove control characters
        sanitized = sanitized.replaceAll("[\\x00-\\x1F\\x7F]", "");

        // Trim whitespace and dots from start/end
        sanitized = sanitized.trim().replaceAll("^[.]+|[.]+$", "");

        // Handle reserved Windows names
        String[] reservedNames = { "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4",
                "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2",
                "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9" };

        String nameWithoutExt = sanitized;
        String extension = "";
        int lastDot = sanitized.lastIndexOf('.');
        if (lastDot > 0) {
            nameWithoutExt = sanitized.substring(0, lastDot);
            extension = sanitized.substring(lastDot);
        }

        for (String reserved : reservedNames) {
            if (nameWithoutExt.equalsIgnoreCase(reserved)) {
                nameWithoutExt = nameWithoutExt + "_file";
                break;
            }
        }

        sanitized = nameWithoutExt + extension;

        // Ensure filename is not too long (max 255 characters)
        if (sanitized.length() > 255) {
            if (lastDot > 0) {
                extension = sanitized.substring(lastDot);
                sanitized = sanitized.substring(0, Math.min(255 - extension.length(), lastDot));
            } else {
                sanitized = sanitized.substring(0, 255);
            }
            sanitized += extension;
        }

        return sanitized.isEmpty() ? "download.tmp" : sanitized;
    }

    private void updateStatus(String status, String size, String progress, String speed) {
        parent.updateDownloadProgress(rowIndex, size, progress, speed, status);
    }

    public void pause() {
        paused = true;
        if (elapsedTimer != null) {
            elapsedTimer.cancel();
        }
    }

    public void resume() {
        paused = false;
        startTime = System.currentTimeMillis(); // Reset start time for accurate speed calculation
        startElapsedTimer();
    }

    public boolean isPaused() {
        return paused;
    }

    public void cancel() {
        cancelled = true;
        stopElapsedTimer();
    }

    public void setRowIndex(int index) {
        this.rowIndex = index;
    }

    public int getRowIndex() {
        return this.rowIndex;
    }
}