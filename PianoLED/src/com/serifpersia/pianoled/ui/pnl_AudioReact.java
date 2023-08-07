package com.serifpersia.pianoled.ui;

import java.awt.Color;

import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import com.serifpersia.pianoled.ModesController;
import com.serifpersia.pianoled.PianoController;
import com.serifpersia.pianoled.PianoLED;

import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JComboBox;
import java.awt.Font;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import javax.swing.JButton;

@SuppressWarnings("serial")
public class pnl_AudioReact extends JPanel {

	private static final int ANALOG_MIN_VALUE = 0;
	private static final int ANALOG_MAX_VALUE = 1023;
	private static final int AUDIO_BUFFER_SIZE = 4096; // Increased buffer size for audio data

	private TargetDataLine line;
	private boolean capturing = false;

	private ModesController modesController;
	private PianoController pianoController;

	public static JComboBox<?> cb_AudioReactLEDEffect;
	private JComboBox<String> cb_AudioDevice;
	private JButton btnStart;;

	public pnl_AudioReact(PianoLED pianoLED) {
		setBackground(new Color(50, 50, 50));

		pianoController = pianoLED.getPianoController();
		modesController = new ModesController(pianoLED);

		init();
		populateAudioInputDevices();
		buttonActions();

	}

	private void init() {
		setBorder(new EmptyBorder(10, 10, 10, 10));

		setLayout(new GridLayout(2, 0, 0, 0));

		JPanel panel = new JPanel();
		panel.setBackground(new Color(50, 50, 50));
		add(panel);
		panel.setLayout(new GridLayout(3, 0, 0, 0));

		JPanel panel_1 = new JPanel();
		panel_1.setBorder(new EmptyBorder(0, 0, 10, 0));
		panel_1.setBackground(new Color(50, 50, 50));
		panel.add(panel_1);
		panel_1.setLayout(new GridLayout(0, 2, 0, 0));

		JLabel lblAudioDevice = new JLabel("Audio Device");
		lblAudioDevice.setHorizontalAlignment(SwingConstants.CENTER);
		lblAudioDevice.setForeground(new Color(208, 208, 208));
		lblAudioDevice.setFont(new Font("Poppins", Font.PLAIN, 21));
		panel_1.add(lblAudioDevice);

		cb_AudioDevice = new JComboBox<String>();
		cb_AudioDevice.putClientProperty("JComponent.roundRect", true);
		cb_AudioDevice.setFont(new Font("Poppins", Font.PLAIN, 21));
		panel_1.add(cb_AudioDevice);

		JPanel panel_2 = new JPanel();
		panel_2.setBorder(new EmptyBorder(0, 0, 10, 0));
		panel_2.setBackground(new Color(50, 50, 50));
		panel.add(panel_2);
		panel_2.setLayout(new GridLayout(0, 2, 0, 0));

		JLabel lblReactLedEffect_1 = new JLabel("React LED Effect");
		lblReactLedEffect_1.setHorizontalAlignment(SwingConstants.CENTER);
		lblReactLedEffect_1.setForeground(new Color(208, 208, 208));
		lblReactLedEffect_1.setFont(new Font("Poppins", Font.PLAIN, 21));
		panel_2.add(lblReactLedEffect_1);

		cb_AudioReactLEDEffect = new JComboBox<Object>(GetUI.ledVisualizerEffectsName.toArray(new String[0]));
		cb_AudioReactLEDEffect.putClientProperty("JComponent.roundRect", true);
		cb_AudioReactLEDEffect.setFont(new Font("Poppins", Font.PLAIN, 21));
		panel_2.add(cb_AudioReactLEDEffect);

		cb_AudioReactLEDEffect.addActionListener(e -> {
			if (ModesController.VisualizerOn) {
				int selectedIndex = cb_AudioReactLEDEffect.getSelectedIndex();
				pianoController.setLedVisualizerEffect(selectedIndex);
			}
		});

		btnStart = new JButton("Start");
		btnStart.setFont(new Font("Poppins", Font.PLAIN, 21));
		btnStart.setBackground(new Color(231, 76, 60));
		panel.add(btnStart);
	}

	private void populateAudioInputDevices() {

		Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
		for (Mixer.Info mixerInfo : mixerInfos) {
			Mixer mixer = AudioSystem.getMixer(mixerInfo);
			Line.Info[] lineInfos = mixer.getTargetLineInfo();
			for (Line.Info lineInfo : lineInfos) {
				if (lineInfo.getLineClass().equals(TargetDataLine.class)) {
					cb_AudioDevice.addItem(mixerInfo.getName());
					break;
				}
			}
		}

	}

	private void buttonActions() {
		ActionListener buttonListener = new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				switch (e.getActionCommand()) {

				case "btnStart":
					if (btnStart.getText().equals("Start")) {

						String selectedDevice = (String) cb_AudioDevice.getSelectedItem();
						// String selectedPort = (String) comPortComboBox.getSelectedItem();
						if (selectedDevice != null) {
							Mixer.Info selectedMixerInfo = getMixerInfoByName(selectedDevice);
							startAudioCapture(selectedMixerInfo);
						}

						btnStart.setBackground(new Color(46, 204, 113));
						btnStart.setForeground(Color.WHITE);
						btnStart.setText("Close");

					} else {

						stopAudioCapture();

						btnStart.setBackground(new Color(231, 76, 60));
						btnStart.setText("Start");
					}
					break;
				default:
					break;
				}
			}
		};

		btnStart.addActionListener(buttonListener);

		btnStart.setActionCommand("btnStart");

	}

	private Mixer.Info getMixerInfoByName(String name) {
		Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
		for (Mixer.Info mixerInfo : mixerInfos) {
			if (mixerInfo.getName().equals(name)) {
				return mixerInfo;
			}
		}
		return null;
	}

	private void startAudioCapture(Mixer.Info selectedMixerInfo) {
		try {
			if (capturing) {
				System.out.println("Already capturing audio.");
				return;
			}

			Mixer mixer = AudioSystem.getMixer(selectedMixerInfo);
			Line.Info[] lineInfos = mixer.getTargetLineInfo();
			line = null;
			for (Line.Info lineInfo : lineInfos) {
				if (lineInfo.getLineClass().equals(TargetDataLine.class)) {
					line = (TargetDataLine) mixer.getLine(lineInfo);
					break;
				}
			}

			if (line == null) {
				System.err.println("Line not supported");
				return;
			}

			AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 22050, 16, 1, 2, 22050, false);
			DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
			if (!AudioSystem.isLineSupported(info)) {
				System.err.println("Line not supported");
				return;
			}

			line.open(format, AUDIO_BUFFER_SIZE); // Set the buffer size for the audio line
			line.start();
			capturing = true;

			System.out.println("Listening with Device: " + selectedMixerInfo.getName());

			SwingWorker<Void, Integer> worker = new SwingWorker<Void, Integer>() {
				@Override
				protected Void doInBackground() throws Exception {
					byte[] buffer = new byte[AUDIO_BUFFER_SIZE];
					int bytesRead;
					while (capturing) {
						bytesRead = line.read(buffer, 0, buffer.length);
						int audioInputValue = processAudioData(buffer, bytesRead);
						publish(audioInputValue);
					}
					return null;
				}

				@Override
				protected void process(java.util.List<Integer> chunks) {
					int audioInputValue = chunks.get(chunks.size() - 1);
					System.out.println("Audio Input Value: " + audioInputValue);
					if (audioInputValue > 0) {
						pianoController.sendAudioDataToArduino(String.valueOf(audioInputValue));
					}
				}

				@Override
				protected void done() {

				}
			};

			worker.execute();

		} catch (LineUnavailableException e) {
			e.printStackTrace();
		}
	}

	private int processAudioData(byte[] audioData, int bytesRead) {
		long sum = 0;
		for (int i = 0; i < bytesRead - 1; i += 2) {
			short sample = (short) ((audioData[i + 1] << 8) | audioData[i]);
			sum += sample * sample;
		}
		double rms = Math.sqrt(sum / (bytesRead / 2));
		return (int) map(rms, 0, Short.MAX_VALUE, ANALOG_MIN_VALUE, ANALOG_MAX_VALUE);
	}

	private double map(double value, double inMin, double inMax, double outMin, double outMax) {
		return (value - inMin) * (outMax - outMin) / (inMax - inMin) + outMin;
	}

	private void stopAudioCapture() {
		capturing = false;
		if (line != null) {
			line.stop();
			line.close();
		}
	}
}
