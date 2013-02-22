/* Generated By:JavaCC: Do not edit this line. PromelaConstants.java */
package spins.promela.compiler.parser;


/**
 * Token literal values and constants.
 * Generated by org.javacc.parser.OtherFilesGen#start()
 */
public interface PromelaConstants {

  /** End of File. */
  int EOF = 0;
  /** RegularExpression Id. */
  int PROCTYPE = 1;
  /** RegularExpression Id. */
  int INIT = 2;
  /** RegularExpression Id. */
  int NEVER = 3;
  /** RegularExpression Id. */
  int TRACE = 4;
  /** RegularExpression Id. */
  int NOTRACE = 5;
  /** RegularExpression Id. */
  int TYPEDEF = 6;
  /** RegularExpression Id. */
  int MTYPE = 7;
  /** RegularExpression Id. */
  int BIT = 8;
  /** RegularExpression Id. */
  int BOOL = 9;
  /** RegularExpression Id. */
  int BYTE = 10;
  /** RegularExpression Id. */
  int PID = 11;
  /** RegularExpression Id. */
  int SHORT = 12;
  /** RegularExpression Id. */
  int INT = 13;
  /** RegularExpression Id. */
  int CHAN = 14;
  /** RegularExpression Id. */
  int PRIORITY = 15;
  /** RegularExpression Id. */
  int PROVIDED = 16;
  /** RegularExpression Id. */
  int HIDDEN = 17;
  /** RegularExpression Id. */
  int SHOW = 18;
  /** RegularExpression Id. */
  int XR = 19;
  /** RegularExpression Id. */
  int XS = 20;
  /** RegularExpression Id. */
  int OF = 21;
  /** RegularExpression Id. */
  int EVAL = 22;
  /** RegularExpression Id. */
  int IF = 23;
  /** RegularExpression Id. */
  int FI = 24;
  /** RegularExpression Id. */
  int DO = 25;
  /** RegularExpression Id. */
  int OD = 26;
  /** RegularExpression Id. */
  int ATOMIC = 27;
  /** RegularExpression Id. */
  int D_STEP = 28;
  /** RegularExpression Id. */
  int ELSE = 29;
  /** RegularExpression Id. */
  int BREAK = 30;
  /** RegularExpression Id. */
  int GOTO = 31;
  /** RegularExpression Id. */
  int PRINT = 32;
  /** RegularExpression Id. */
  int ASSERT = 33;
  /** RegularExpression Id. */
  int LEN = 34;
  /** RegularExpression Id. */
  int TIMEOUT = 35;
  /** RegularExpression Id. */
  int NP_ = 36;
  /** RegularExpression Id. */
  int ENABLED = 37;
  /** RegularExpression Id. */
  int PC_VALUE = 38;
  /** RegularExpression Id. */
  int RUN = 39;
  /** RegularExpression Id. */
  int FULL = 40;
  /** RegularExpression Id. */
  int EMPTY = 41;
  /** RegularExpression Id. */
  int NFULL = 42;
  /** RegularExpression Id. */
  int NEMPTY = 43;
  /** RegularExpression Id. */
  int TRUE = 44;
  /** RegularExpression Id. */
  int FALSE = 45;
  /** RegularExpression Id. */
  int SKIP_ = 46;
  /** RegularExpression Id. */
  int UNLESS = 47;
  /** RegularExpression Id. */
  int VAR_PID = 48;
  /** RegularExpression Id. */
  int LCURLY = 49;
  /** RegularExpression Id. */
  int RCURLY = 50;
  /** RegularExpression Id. */
  int ASSIGN = 51;
  /** RegularExpression Id. */
  int LPAREN = 52;
  /** RegularExpression Id. */
  int RPAREN = 53;
  /** RegularExpression Id. */
  int LBRACK = 54;
  /** RegularExpression Id. */
  int RBRACK = 55;
  /** RegularExpression Id. */
  int OPTION = 56;
  /** RegularExpression Id. */
  int COLON = 57;
  /** RegularExpression Id. */
  int SEMICOLON = 58;
  /** RegularExpression Id. */
  int COMMA = 59;
  /** RegularExpression Id. */
  int RARROW = 60;
  /** RegularExpression Id. */
  int CH_SEND_SORTED = 61;
  /** RegularExpression Id. */
  int CH_READ = 62;
  /** RegularExpression Id. */
  int CH_READ_RAND = 63;
  /** RegularExpression Id. */
  int AT = 64;
  /** RegularExpression Id. */
  int BNOT = 65;
  /** RegularExpression Id. */
  int LNOT = 66;
  /** RegularExpression Id. */
  int MINUS = 67;
  /** RegularExpression Id. */
  int TIMES = 68;
  /** RegularExpression Id. */
  int DIVIDE = 69;
  /** RegularExpression Id. */
  int MODULO = 70;
  /** RegularExpression Id. */
  int PLUS = 71;
  /** RegularExpression Id. */
  int LSHIFT = 72;
  /** RegularExpression Id. */
  int RSHIFT = 73;
  /** RegularExpression Id. */
  int LT = 74;
  /** RegularExpression Id. */
  int LTE = 75;
  /** RegularExpression Id. */
  int GT = 76;
  /** RegularExpression Id. */
  int GTE = 77;
  /** RegularExpression Id. */
  int EQ = 78;
  /** RegularExpression Id. */
  int NEQ = 79;
  /** RegularExpression Id. */
  int BAND = 80;
  /** RegularExpression Id. */
  int XOR = 81;
  /** RegularExpression Id. */
  int BOR = 82;
  /** RegularExpression Id. */
  int LAND = 83;
  /** RegularExpression Id. */
  int LOR = 84;
  /** RegularExpression Id. */
  int INCR = 85;
  /** RegularExpression Id. */
  int DECR = 86;
  /** RegularExpression Id. */
  int DOT = 87;
  /** RegularExpression Id. */
  int IDENTIFIER = 97;
  /** RegularExpression Id. */
  int NUMBER = 98;
  /** RegularExpression Id. */
  int DEFINE = 187;
  /** RegularExpression Id. */
  int PARAM = 192;
  /** RegularExpression Id. */
  int STRING = 214;

  /** Lexical state. */
  int DEFAULT = 0;
  /** Lexical state. */
  int IN_STRING = 1;
  /** Lexical state. */
  int IN_COMMENT = 2;
  /** Lexical state. */
  int IN_COMMENT2 = 3;
  /** Lexical state. */
  int PREPROCESSOR_INCLUDE_FILE = 4;
  /** Lexical state. */
  int PREPROCESSOR_INCLUDE = 5;
  /** Lexical state. */
  int PREPROCESSOR_DEFINE_REST = 6;
  /** Lexical state. */
  int PREPROCESSOR_DEFINE_PARAM2 = 7;
  /** Lexical state. */
  int PREPROCESSOR_DEFINE_PARAM1 = 8;
  /** Lexical state. */
  int PREPROCESSOR_DEFINE_PARAM = 9;
  /** Lexical state. */
  int PREPROCESSOR_DEFINE = 10;
  /** Lexical state. */
  int PREPROCESSOR_INLINE_REST = 11;
  /** Lexical state. */
  int PREPROCESSOR_INLINE_TEXT = 12;
  /** Lexical state. */
  int PREPROCESSOR_INLINE_PARAM2 = 13;
  /** Lexical state. */
  int PREPROCESSOR_INLINE_PARAM1 = 14;
  /** Lexical state. */
  int PREPROCESSOR_INLINE_PARAM0 = 15;
  /** Lexical state. */
  int PREPROCESSOR_INLINE_PARAM = 16;
  /** Lexical state. */
  int PREPROCESSOR_INLINE = 17;
  /** Lexical state. */
  int PREPROCESSOR_LINE = 18;
  /** Lexical state. */
  int PREPROCESSOR_FILE = 19;
  /** Lexical state. */
  int PREPROCESSOR_ELIF_SKIP = 20;
  /** Lexical state. */
  int PREPROCESSOR_SKIP = 21;
  /** Lexical state. */
  int PREPROCESSOR_IFNDEF = 22;
  /** Lexical state. */
  int PREPROCESSOR_IFDEF = 23;
  /** Lexical state. */
  int PREPROCESSOR_IF = 24;
  /** Lexical state. */
  int PREPROCESSOR_NDEFINED = 25;
  /** Lexical state. */
  int PREPROCESSOR_DEFINED = 26;

  /** Literal token values. */
  String[] tokenImage = {
    "<EOF>",
    "\"proctype\"",
    "\"init\"",
    "\"never\"",
    "\"trace\"",
    "\"notrace\"",
    "\"typedef\"",
    "\"mtype\"",
    "\"bit\"",
    "\"bool\"",
    "\"byte\"",
    "\"pid\"",
    "\"short\"",
    "\"int\"",
    "\"chan\"",
    "\"priority\"",
    "\"provided\"",
    "\"hidden\"",
    "\"show\"",
    "\"xr\"",
    "\"xs\"",
    "\"of\"",
    "\"eval\"",
    "\"if\"",
    "\"fi\"",
    "\"do\"",
    "\"od\"",
    "\"atomic\"",
    "\"d_step\"",
    "\"else\"",
    "\"break\"",
    "\"goto\"",
    "\"printf\"",
    "\"assert\"",
    "\"len\"",
    "\"timeout\"",
    "\"np_\"",
    "\"enabled\"",
    "\"pc_value\"",
    "\"run\"",
    "\"full\"",
    "\"empty\"",
    "\"nfull\"",
    "\"nempty\"",
    "\"true\"",
    "\"false\"",
    "\"skip\"",
    "\"unless\"",
    "\"_pid\"",
    "\"{\"",
    "\"}\"",
    "\"=\"",
    "\"(\"",
    "\")\"",
    "\"[\"",
    "\"]\"",
    "\"::\"",
    "\":\"",
    "\";\"",
    "\",\"",
    "\"->\"",
    "\"!!\"",
    "\"?\"",
    "\"??\"",
    "\"@\"",
    "\"~\"",
    "\"!\"",
    "\"-\"",
    "\"*\"",
    "\"/\"",
    "\"%\"",
    "\"+\"",
    "\"<<\"",
    "\">>\"",
    "\"<\"",
    "\"<=\"",
    "\">\"",
    "\">=\"",
    "\"==\"",
    "\"!=\"",
    "\"&\"",
    "\"^\"",
    "\"|\"",
    "\"&&\"",
    "\"||\"",
    "\"++\"",
    "\"--\"",
    "\".\"",
    "\"inline\"",
    "\"defined\"",
    "\"ndef\"",
    "\"(\"",
    "\" \"",
    "<token of kind 93>",
    "\"(\"",
    "\" \"",
    "<token of kind 96>",
    "<IDENTIFIER>",
    "<NUMBER>",
    "\" \"",
    "\"\\r\"",
    "\"\\t\"",
    "\"\\n\"",
    "\"/*\"",
    "\"//\"",
    "\"#if\"",
    "\"#ifdef\"",
    "\"#ifndef\"",
    "\"#else\"",
    "\"#elif\"",
    "\"#endif\"",
    "\"#line\"",
    "\"#file\"",
    "\"#define\"",
    "\"#include\"",
    "\"#\"",
    "<token of kind 116>",
    "\"\\\\\\n\"",
    "\"\\\\\\r\"",
    "\"\\\\\\r\\n\"",
    "<token of kind 120>",
    "<token of kind 121>",
    "<token of kind 122>",
    "<token of kind 123>",
    "<token of kind 124>",
    "<token of kind 125>",
    "\"#else\"",
    "\"#elif\"",
    "\"#endif\"",
    "<token of kind 129>",
    "<token of kind 130>",
    "\"\\\\\\n\"",
    "\"\\\\\\r\"",
    "\"\\\\\\r\\n\"",
    "<token of kind 134>",
    "<token of kind 135>",
    "\"\\\\\\n\"",
    "\"\\\\\\r\"",
    "\"\\\\\\r\\n\"",
    "<token of kind 139>",
    "<token of kind 140>",
    "\"\\\\\\n\"",
    "\"\\\\\\r\"",
    "\"\\\\\\r\\n\"",
    "<token of kind 144>",
    "\" \"",
    "\"\\t\"",
    "\"\\n\"",
    "\"\\r\"",
    "\"\\r\\n\"",
    "<token of kind 150>",
    "\" \"",
    "\"\\t\"",
    "\"\\n\"",
    "\"\\r\"",
    "\"\\r\\n\"",
    "\"(\"",
    "<token of kind 157>",
    "\" \"",
    "\"\\t\"",
    "\"\\n\"",
    "\"\\r\"",
    "\"\\r\\n\"",
    "<token of kind 163>",
    "\")\"",
    "<token of kind 165>",
    "\" \"",
    "\"\\t\"",
    "\"\\n\"",
    "\"\\r\"",
    "\"\\r\\n\"",
    "<token of kind 171>",
    "<token of kind 172>",
    "\" \"",
    "\"\\t\"",
    "\"\\n\"",
    "\"\\r\"",
    "\"\\r\\n\"",
    "\",\"",
    "\")\"",
    "<token of kind 180>",
    "<token of kind 181>",
    "\"{\"",
    "\"{\"",
    "\"}\"",
    "<token of kind 185>",
    "<token of kind 186>",
    "<DEFINE>",
    "<token of kind 188>",
    "\"(\"",
    "<token of kind 190>",
    "<token of kind 191>",
    "<PARAM>",
    "<token of kind 193>",
    "<token of kind 194>",
    "\",\"",
    "\")\"",
    "<token of kind 197>",
    "<token of kind 198>",
    "\"\\\\\\n\"",
    "\"\\\\\\r\"",
    "\"\\\\\\r\\n\"",
    "<token of kind 202>",
    "<token of kind 203>",
    "\"\\\"\"",
    "\"\\\"\"",
    "<token of kind 206>",
    "<token of kind 207>",
    "<token of kind 208>",
    "\"/*\"",
    "<token of kind 210>",
    "\"*/\"",
    "\"\\\"\"",
    "<token of kind 213>",
    "\"\\\"\"",
  };

}