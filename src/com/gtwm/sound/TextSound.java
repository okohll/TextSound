package com.gtwm.sound;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.List;
import org.jfugue.MicrotoneNotation;
import org.jfugue.Player;

/**
 * Turn a stream of text into sound, using the overtone series
 * Letter A = root note
 * B = first overtone, double the frequency, one octave above
 * C = triple the frequency, an octave plus a fifth above the root
 * D = x4 frequency, two octaves up
 * E = x5, two octaves plus a third etc.
 * 
 * Higher notes are normalised down to fit in the octave range specified
 * 
 * @author oliver
 */
public class TextSound {

	private static final String outFilename = "/Users/oliver/Downloads/textSound.mid";
	private static final String inFilename = "/Users/oliver/Downloads/textSound.txt";
	/**
	 * How long to hold each note for
	 */
	private static final String noteLength = "/2"; // /1 = whole note (semibreve)
	/**
	 * How long to wait before playing the next note
	 */
	private static final String noteGap = "/0";// + String.valueOf(1/(double) 32); // 1/32 = good default
	/**
	 * How long to pause when a rest (space etc.) is encountered
	 */
	private static final String restLength = "/" + String.valueOf(1/(double) 8); // 1/8 = good default
	/**
	 * Lowest note that can be played
	 */
	private static final int baseFrequency = 64; // 128 Hz = Octave below middle C
	/**
	 * Octave range in which to place notes
	 */
	private static final int octaves = 3;
	/**
	 * Tempo in beats per second
	 */
	private static final int initialTempo = 100;
	/**
	 * Whether to dynamically alter tempo based on punctuation
	 */
	private static final boolean dynamicTempo = true;
	/**
	 * MIDI instrument
	 */
	private static final String instrument = "PIANO";
	
	public static void main(String[] args) throws Exception {
		
		List<String> lines = Files.readAllLines(FileSystems.getDefault().getPath(inFilename), StandardCharsets.UTF_8);
		StringBuilder inBuilder = new StringBuilder();
		for (String line : lines) {
			inBuilder.append(line);
		}
		String input = inBuilder.toString();
		//input = "It was the best of times, it was the worst of times";
		//input = "The quality of implementation specifications concern two properties, accuracy of the returned result and monotonicity of the method";
		//input = "In certain service environments, particularly those where the business environment is dynamic and where clients interact with the organisation on a regular basis, we have found a grasp of Systems Thinking is invaluable.";
		//input = "The Sixth Crusade started in 1228 as an attempt to regain Jerusalem";
		//input = "Although Baden-Württemberg is lacking natural resources,[2] the state is among the most prosperous states in Germany";
		//input = "If you want to print this guide, it is recommended that you print in Landscape mode";
		//input = "Effective content marketing holds people’s attention. It gives you a distinctive brand, loyal fans and increased sales. You don’t need a big budget to succeed, which is why good content marketing is the single best way to beat bigger competitors online";
		//input = "It was the best of times, it was the worst of times, it was the age of wisdom, it was the age of foolishness, it was the epoch of belief, it was the epoch of incredulity, it was the season of Light, it was the season of Darkness, it was the spring of hope, it was the winter of despair, we had everything before us, we had nothing before us, we were all going direct to heaven, we were all going direct the other way - in short, the period was so far like the present period, that some of its noisiest authorities insisted on its being received, for good or for evil, in the superlative degree of comparison only.";
		//input = "The municipality was created on 14 March 1997, succeeding the sub-provincial city administration that was part of Sichuan Province";
		input = input.toUpperCase();
		StringBuilder soundString = new StringBuilder("T" + initialTempo + " I[" + instrument + "] ");
		int octave = octaves;
		int tempo = initialTempo;
		int lastTempo = initialTempo;
		int actualTempo = initialTempo;
		for (int charIndex = 0; charIndex < input.length(); charIndex++) {
			char ch = input.charAt(charIndex);
			// A = 1, B = 2, ...
			int charNum = Character.getNumericValue(ch) - 9;
			if ((Character.isWhitespace(ch)) || (charNum < 1)) {
				soundString.append("R" + restLength + " ");
				if (Character.isDigit(ch)) {
					// TODO: per note volume (X[Volume] is overall volume)
					//soundString += "X[Volume]="+ Math.abs(charNum) * (16000/9) + " ";
				} else if ((!Character.isWhitespace(ch)) && dynamicTempo) {
					tempo = Math.abs(Character.valueOf(ch).hashCode() % 400);
					if (tempo == lastTempo) {
						actualTempo = (int) ((double) actualTempo * 1.5);
					} else {
						actualTempo = tempo;
					}
					soundString.append("T"+ actualTempo + " ");
					System.out.println("" + ch + ": " + Character.getType(ch) + ", T" + tempo);
					lastTempo = tempo;
				}
				// Every time there's a rest, reset the top octave based on the first letter of the next word
				if ((charIndex+1) < input.length()) {
					char nextChar = input.charAt(charIndex + 1);
					int nextCharNum = Character.getNumericValue(nextChar) - 9;
					if (nextCharNum < 1) {
						nextCharNum = 1;
					}
					octave = 1 + ((nextCharNum - 1) % octaves);
				}
			} else {
				double frequency = charNum * baseFrequency;
				// Normalise to fit in the range
				int topFrequency = baseFrequency;
				for (int j = 0; j < octave; j++) {
					topFrequency = topFrequency * 2;
				}
				while (frequency > topFrequency) {
					frequency = frequency / 2;
				}
				System.out.println("Frequency for " + ch + "=" + charNum + " normalized to octave " + octave +", top frequency " + topFrequency + ": " + frequency);
				soundString.append(MicrotoneNotation.convertFrequencyToMusicString(frequency));
				soundString.append(noteLength + "+R" + noteGap + " ");
			}
		}
		Player player = new Player();
		System.out.println(soundString);
		File file = new File(outFilename);
		String ss = soundString.toString();
		player.saveMidi(ss, file);
		player.play(ss); 
	}
	
}
