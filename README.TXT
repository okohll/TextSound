Turn a stream of text into sound, using the overtone series

Letter A = root note
B = first overtone, double the frequency, one octave above
C = triple
the frequency, an octave plus a fifth above the root
D = x4 frequency, two octaves up
E = x5, two octaves plus a third etc.

Higher notes are normalised down to fit into a reasonable octave range

Other properties of the text like punctuation and word type affect other sound properties, such as volume, tempo, range etc.

Aim:
To transform text into sound (not necessarily music) with aesthetically pleasing and/or interesting output.

Output should be completely deterministic, i.e. there should be no randomness - the same input text will always produce the same sound

=== Usage ===

java -jar textsound.jar myfile.txt

=== Requirements ===

Java 7