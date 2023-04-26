package com.serifpersia.pianoled;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiDevice.Info;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import com.serifpersia.pianoled.learn.PianoMidiConsumer;
import com.serifpersia.pianoled.learn.PianoReceiver;
import com.serifpersia.pianoled.ui.ControlsPanel;
import com.serifpersia.pianoled.ui.DashboardPanel;
import com.serifpersia.pianoled.ui.GetUI;

import jssc.SerialPortList;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.BufferedReader;

public class PianoController implements PianoMidiConsumer {

	private PianoLED pianoLED;

	public Arduino arduino;
	public String portName;
	public String[] portNames = SerialPortList.getPortNames();

	public Color splitLeftColor = Color.RED;
	public Color splitRightColor = Color.BLUE;

	public Color LeftSideColor = Color.RED;
	public Color MiddleSideColor = Color.GREEN;
	public Color RightSideColor = Color.BLUE;

	private List<PianoMidiConsumer> consumers = new ArrayList<>();

	private PianoReceiver pianoReceiver;

	public PianoController(PianoLED pianoLED) {
		this.pianoLED = pianoLED;
	}

	public void addPianoMidiConsumer(PianoMidiConsumer consumer) {
		this.consumers.add(consumer);
	}

	public void findPortNameOnWindows(String deviceName) {
		String[] cmd = { "cmd", "/c",
				"wmic path Win32_PnPEntity where \"Caption like '%(COM%)'\" get Caption /format:table" };
		portName = null;
		try {
			Process p = Runtime.getRuntime().exec(cmd);
			BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.contains(deviceName)) {
					String[] tokens = line.split("\\s+");
					portName = tokens[tokens.length - 1].replaceAll("[()]", "");
					System.out.println("Serial Device detected: " + line);
					break;
				}
			}
			reader.close();
		} catch (IOException e) {
			System.out.println("Error: " + e.getMessage());
		}
	}

	public void refreshSerialList() {
		// Get the index of the portName in the portNames array
		int index = Arrays.asList(portNames).indexOf(portName);

		// Select the corresponding item in the SerialList JComboBox object
		if (index >= 0) {
			DashboardPanel.SerialList.setSelectedIndex(index);
		}
	}

	public void refreshMidiList() {
		// Get the list of available MIDI devices
		ArrayList<Info> devices = getMidiDevices();

		// Find the index of the first device whose name contains "piano" or "midi"
		int index = 0;
		for (Info device : devices) {
			if (device.getName().toLowerCase().contains("piano") || device.getName().toLowerCase().contains("midi")) {
				break;
			}
			index++;
		}

		// Set the corresponding item in the MidiList JComboBox object
		if (index >= 0) {
			DashboardPanel.MidiList.setSelectedIndex(index);
		}
	}

	public void findPortNameOnLinux(String deviceName) {
		String[] cmd = { "sh", "-c", "dmesg | grep " + deviceName };
		portName = null;
		Pattern pattern = Pattern.compile(deviceName + "(\\d+)");
		try {
			Process p = Runtime.getRuntime().exec(cmd);
			BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line;
			while ((line = reader.readLine()) != null) {
				Matcher matcher = pattern.matcher(line);
				if (matcher.find()) {
					portName = "/dev/" + matcher.group(0);
					System.out.println("Device found: " + portName);
					break;
				}
			}
			reader.close();
		} catch (IOException e) {
			System.out.println("Error: " + e.getMessage());
		}
	}

	public String[] getMidiOutDevices() {
		MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
		ArrayList<String> deviceNames = new ArrayList<String>();
		for (MidiDevice.Info info : infos) {
			MidiDevice device = null;
			try {
				device = MidiSystem.getMidiDevice(info);
				if (device.getMaxReceivers() != 0) {
					deviceNames.add(info.getName());
				}
			} catch (MidiUnavailableException ex) {
				System.err.println("Error getting MIDI device " + info.getName() + ": " + ex.getMessage());
			} finally {
				if (device != null) {
					device.close();
				}
			}
		}
		if (deviceNames.isEmpty()) {
			return new String[] { "No MIDI out devices available" };
		} else {
			return deviceNames.toArray(new String[deviceNames.size()]);
		}
	}

	public ArrayList<Info> getMidiDevices() {
		Info[] infos = MidiSystem.getMidiDeviceInfo();
		ArrayList<Info> deviceNames = new ArrayList<Info>();
		for (MidiDevice.Info info : infos) {
			MidiDevice device = null;
			try {
				device = MidiSystem.getMidiDevice(info);
				if (device.getMaxTransmitters() != 0) {
					deviceNames.add(info);
				}
			} catch (MidiUnavailableException ex) {
				System.err.println("Error getting MIDI device " + info.getName() + ": " + ex.getMessage());
			} finally {
				if (device != null) {
					device.close();
				}
			}
		}
		return deviceNames;
	}

	public void openMidi() {
		Info deviceInfo = (Info) DashboardPanel.MidiList.getSelectedItem();
		MidiDevice device = null;
		try {
			device = MidiSystem.getMidiDevice(deviceInfo);
			device.open();
			pianoReceiver = new PianoReceiver();
			pianoReceiver.addConsumer(this);
			device.getTransmitter().setReceiver(pianoReceiver);
			System.out.println("MIDI device " + deviceInfo + " opened successfully.");

		} catch (MidiUnavailableException ex) {
			System.err.println("Error opening MIDI device " + deviceInfo + ": " + ex.getMessage());
		}
	}

	public void closeMidi() {
		Info deviceInfo = (Info) DashboardPanel.MidiList.getSelectedItem();
		MidiDevice device = null;
		try {
			device = MidiSystem.getMidiDevice(deviceInfo);
			device.close();
			System.out.println("MIDI device " + deviceInfo + " closed successfully.");
		} catch (MidiUnavailableException ex) {
			System.err.println("Error closing MIDI device " + deviceInfo + ": " + ex.getMessage());
		}
	}

	public boolean useFixedMapping = false;
	public boolean stripReverse = false; // default value
	public boolean bgToggle = false; // default value

	// Map function maps pitch first last note and number of leds
	public int mapMidiNoteToLED(int midiNote, int lowestNote, int highestNote, int stripLEDNumber, int outMin) {
		int outMax = outMin + stripLEDNumber - 1; // highest LED number
		int mappedLED = (midiNote - lowestNote) * (outMax - outMin) / (highestNote - lowestNote);
		return mappedLED + outMin;
	}

	public int mapMidiNoteToLEDFixed(int midiNote, int lowestNote, int highestNote, int stripLEDNumber, int outMin) {
		int outMax = outMin + stripLEDNumber - 1; // highest LED number
		int mappedLED = (midiNote - lowestNote) * (outMax - outMin) / (highestNote - lowestNote);

		if (midiNote >= 57) {
			mappedLED -= 1;
		}

		if (midiNote >= 93) {
			mappedLED -= 1;
		}
		return mappedLED + outMin;
	}

	public void noteOn(int channel, int pitch, int velocity) {
		int notePushed;
		if (useFixedMapping) {
			notePushed = mapMidiNoteToLEDFixed(pitch, GetUI.getFirstNoteSelected(), GetUI.getLastNoteSelected(),
					GetUI.getStripLedNum(), 1);
		} else {
			notePushed = mapMidiNoteToLED(pitch, GetUI.getFirstNoteSelected(), GetUI.getLastNoteSelected(),
					GetUI.getStripLedNum(), 1);
		}

		pianoLED.setPianoKey(pitch, 1);
		try {
			ByteArrayOutputStream message = null;

			if (!ModesController.AnimationOn) {
				if (ModesController.RandomOn) {
					Random rand = new Random();
					message = arduino.commandSetColor(
							new Color(rand.nextInt(250) + 1, rand.nextInt(250) + 1, rand.nextInt(250) + 1), notePushed);
				} else if (ModesController.VelocityOn) {
					message = arduino.commandVelocity(velocity, notePushed, ControlsPanel.selectedColor);
				} else if (ModesController.SplitOn) {
					System.out.println("Left Side Color: " + pitch + " " + GetUI.getLeftMinPitch() + " "
							+ GetUI.getLeftMaxPitch());
					if (pitch >= GetUI.getLeftMinPitch() && pitch <= GetUI.getLeftMaxPitch() - 1) {
						System.out.println("Left Side Color: " + pitch + " " + GetUI.getLeftMinPitch() + " "
								+ GetUI.getLeftMaxPitch());
						message = arduino.commandSetColor(splitLeftColor, notePushed);
					} else if (pitch > GetUI.getLeftMaxPitch() - 1 && pitch <= GetUI.getRightMaxPitch()) {
						System.out.println("Right Side Color");
						message = arduino.commandSetColor(splitRightColor, notePushed);
					}
				} else if (ModesController.GradientOn) {
					int numSteps = GetUI.getStripLedNum() - 1;
					int step = notePushed - 1;
					float ratio = (float) step / (float) numSteps;

					Color startColor = LeftSideColor;
					Color endColor = RightSideColor;
					Color currentColor;

					if (!MiddleSideColor.equals(Color.BLACK)) {
						if (step < numSteps / 2) {
							endColor = MiddleSideColor;
							ratio = 1.33f * ratio;
						} else {
							startColor = MiddleSideColor;
							ratio = 2 * (ratio - 0.5f);
						}
					}
					int red = (int) (startColor.getRed() + (endColor.getRed() - startColor.getRed()) * ratio);
					int green = (int) (startColor.getGreen() + (endColor.getGreen() - startColor.getGreen()) * ratio);
					int blue = (int) (startColor.getBlue() + (endColor.getBlue() - startColor.getBlue()) * ratio);
					currentColor = new Color(red, green, blue);

					message = arduino.commandSetColor(currentColor, notePushed);
				} else if (ModesController.SplashOn) {
					message = arduino.commandSplash(velocity, notePushed, getSplashColor());

				} else {
					if (arduino != null)
						message = arduino.commandSetColor(ControlsPanel.selectedColor, notePushed);
				}

				if (message != null) {
					arduino.sendToArduino(message);
				}
			}

			for (PianoMidiConsumer consumer : consumers) {
				consumer.onPianoKeyOn(pitch, velocity);
			}

		} catch (Exception e) {
			System.out.println("Error sending command: " + e);
		}
	}

	public void noteOff(int channel, int pitch, int velocity) {
		int notePushed;
		if (useFixedMapping) {
			notePushed = mapMidiNoteToLEDFixed(pitch, GetUI.getFirstNoteSelected(), GetUI.getLastNoteSelected(),
					GetUI.getStripLedNum(), 1);
		} else {
			notePushed = mapMidiNoteToLED(pitch, GetUI.getFirstNoteSelected(), GetUI.getLastNoteSelected(),
					GetUI.getStripLedNum(), 1);
		}
		pianoLED.setPianoKey(pitch, 0);
		try {
			if (!ModesController.AnimationOn) {
				arduino.sendCommandKeyOff(notePushed);
			}
		} catch (Exception e) {
		}
	}

	public void FadeRate(int value) {
		if (arduino != null) {
			arduino.sendCommandFadeRate(value);
		}
	}

	public void BrightnessRate(int value) {
		if (arduino != null) {
			arduino.sendCommandBrightness(value);
		}
	}

	public Color getSplashColor() {
		return new Color(ControlsPanel.selectedColor.getRGB());
	}

	public void SplashLengthRate(int value) {
		if (arduino != null) {
			arduino.sendCommandSplashMaxLength(value);
		}
	}

	public void stripReverse(boolean on) {
		arduino.sendCommandStripDirection(on ? 1 : 0, GetUI.getStripLedNum());
	}

	public void setLedBG(boolean on) {
		int BG_HUE = 100;
		int BG_SATURATION = 0;
		int BG_BRIGHTNESS = 20;

		if (on) {
			if (arduino != null)
				arduino.sendCommandSetBG(BG_HUE, BG_SATURATION, BG_BRIGHTNESS);
		} else {
			arduino.sendCommandSetBG(0, 0, 0);
		}
	}

	public void setBG() {
		int red = ControlsPanel.selectedColor.getRed();
		int green = ControlsPanel.selectedColor.getGreen();
		int blue = ControlsPanel.selectedColor.getBlue();

		float[] hsbValues = Color.RGBtoHSB(red, green, blue, null);
		int hue = (int) (hsbValues[0] * 255);
		int saturation = (int) (hsbValues[1] * 255);
		int brightness = 30;

		if (arduino != null)
			arduino.sendCommandSetBG(hue, saturation, brightness);
	}

	public void animationlist(int n) {
		if (arduino != null)
			arduino.sendCommandAnimation(n);
	}

	public void dispose() {
		// Dispose your app here
		try {
			if (arduino != null) {
				arduino.sendCommandBlackOut();
				arduino.sendCommandSetBG(0, 0, 0);
				arduino.stop();
			}
		} catch (Exception e) {
			System.out.println("Error while exiting: " + e);
		}
	}

	@Override
	public void onPianoKeyOn(int pitch, int velocity) {
		noteOn(0, pitch, velocity);
	}

	@Override
	public void onPianoKeyOff(int pitch) {
		noteOff(0, pitch, 0);
	}
}