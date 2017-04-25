package magyarlanc;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

public class GUI
{
    private static JTextArea textarea;
    private static JLabel imageLabel;

    private static String _sentenceAsString(String[][] array)
    {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < array.length; i++)
        {
            for (int j = 0; j < array[i].length; j++)
            {
                sb.append(array[i][j]).append('\t');
            }
            sb.append('\n');
        }

        return sb.toString();
    }

    private static void _moveToCenter(Component component)
    {
        component.setLocation(
            (int) ((Toolkit.getDefaultToolkit().getScreenSize().getWidth() - component.getPreferredSize().getWidth()) / 2),
            (int) ((Toolkit.getDefaultToolkit().getScreenSize().getHeight() - component.getPreferredSize().getHeight()) / 2));
    }

    public static void init()
    {
        JFrame frame = new JFrame("magyarlanc 2.0");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setLayout(new BorderLayout());

        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new BoxLayout(inputPanel, BoxLayout.X_AXIS));
        inputPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JTextField inputField = new JTextField(
            "Nehéz lesz megszokni a sok üres épületet, de a kínai áruházak hamar pezsgővé változtathatják a szellemházakat.");

        JButton sendButton = new JButton("OK");
        sendButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent actionEvent)
            {
                if (inputField.getText() != null && !inputField.getText().equals(""))
                {
                    String[] sentence = HunSplitter.splitToArray(inputField.getText())[0];

                    String[][] depParsed = Dependency.depParseSentence(sentence);

                    BufferedImage bufferedImage = null;
                    try
                    {
                        bufferedImage = ImageIO.read(new ByteArrayInputStream(WhatsWrong.exportToByteArray(depParsed)));
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }

                    if (imageLabel != null)
                        imageLabel.setVisible(false);

                    imageLabel = new JLabel(new ImageIcon(bufferedImage));
                    frame.getContentPane().add(imageLabel, "Center");

                    if (textarea != null)
                        textarea.setVisible(false);

                    textarea = new JTextArea();
                    textarea.setText(_sentenceAsString(depParsed));
                    textarea.setMargin(new Insets(10, 10, 10, 10));
                    frame.getContentPane().add(textarea, "South");

                    _moveToCenter(frame);
                    frame.pack();
                    frame.setVisible(true);
                }
            }
        });

        inputPanel.add(inputField);
        inputPanel.add(sendButton);

        frame.getContentPane().add(inputPanel, "North");
        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();

        frame.setPreferredSize(new Dimension((int) dim.getWidth() - 150, (int) dim.getHeight() - 150));
        frame.setResizable(false);

        _moveToCenter(frame);

        frame.pack();
        frame.setVisible(true);
    }

    public static void main(String[] args)
    {
        init();
    }
}
