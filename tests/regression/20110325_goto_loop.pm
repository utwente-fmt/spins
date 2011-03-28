/* Author: Stefan Vijzelaar <s.j.j.vijzelaar@vu.nl>
 * Date: 2011-03-25
 * SPIN: 0.9.2
 * Description: A goto referencing itself causes an endless loop in the 
 * graph optimizer for removing useless gotos.
 */

init {
	label: goto label;
}
