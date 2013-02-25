#include <pthread.h>

static const size_t 	DB_INIT_SIZE = 4;
static const size_t 	DB_MAX_SIZE = 15;

#define cas(a, b, c) __sync_bool_compare_and_swap(a,b,c)

typedef struct spins_args_s {
	void			   *model;
	void (*callback)(void* arg, transition_info_t *transition_info, state_t *out);
	void 			   *arg;
	size_t 				outs;
	int 				sid;
	transition_info_t  *ti_orig;
} spins_args_t;

extern void spins_dfs (spins_args_t *args, state_t *state, int atomic);

void
spins_atomic_cb (void* arg, transition_info_t *transition_info, state_t *out, int atomic)
{
	spins_args_t *args = (spins_args_t *)arg;
	if (leaves_atomic[transition_info->group]) {
		args->callback (args->arg, args->ti_orig, out);
		args->outs++;
	} else {
		spins_dfs (args, out, atomic);
	}
}

void
spins_dfs (spins_args_t *args, state_t *state, int atomic)
{
    state_t out;
    int count = spins_get_successor_sid (args->model, state, args, &out, atomic);
    if (count == 0) {
        args->callback (args->arg, args->ti_orig, state);
        args->outs++;
    }
}

void
spins_free_args (void *a)
{
    spins_args_t *args = a;
    free (args);
}

static pthread_key_t spins_local_key;

__attribute__((constructor)) void
spins_initialize_key() {
    pthread_key_create (&spins_local_key, spins_free_args);
}

__attribute__((destructor)) void
spins_destroy_key() {
    pthread_key_delete (spins_local_key);
}

inline int
spins_reach (void* model, transition_info_t *transition_info, state_t *in,
	   void (*callback)(void* arg, transition_info_t *transition_info, state_t *out),
	   void *arg, int sid) {
	spins_args_t args;
	args.model = model;
	args.callback = callback;
	args.arg = arg;
	args.outs = 0;
	args.sid = sid;
	args.ti_orig = transition_info;
	spins_dfs (&args, in, sid);
	return args.outs;
}

static int spins_to_get;
static int spins_choice;
static int spins_pilot = false;
static int spins_match_tid = false;
static int statement_type = -1;

void
spins_sim_cb(void* arg, transition_info_t *ti, state_t *out)
{
	state_t *state = (state_t *)arg;
	if (-1 == spins_to_get) {
		printf("\tchoice %d: %s\n", ++spins_choice, spins_get_type_value_name(statement_type, ti->group));
	} else {
		++spins_choice;
		if (spins_match_tid ? ti->group == spins_to_get : spins_choice == spins_to_get) {
			memcpy(state, out, sizeof(state_t));
		}
	}
}

void
spins_print_state(state_t *state)
{
	int *s = (int *)state;
	int i;
	for (i = 0; i < spins_get_state_size(); i++) {
		printf("%-30s", spins_get_state_variable_name(i));
		printf("= ");
		int type = spins_get_state_variable_type(i);
		int c = spins_get_type_value_count(type);
		if (0 == c) {
			printf("%3d\n", s[i]);
		} else {
			printf("%s\n", spins_get_type_value_name(type, s[i]));
		}
	}
}

void
spins_dm()
{
	int i, j;
	int k = spins_get_transition_groups();
	int n = spins_get_state_size();
	for (i = 0; i < k; i++) {
		printf("%d)\t%s\t", i, spins_get_type_value_name(statement_type, i));
		const int *write = spins_get_transition_write_dependencies(i);
		const int *read = spins_get_transition_read_dependencies(i);
		for (j = 0; j < n; j++) {
			if (read[j]) printf("R(%s), ", spins_get_state_variable_name(j));
			if (write[j]) printf("W(%s), ", spins_get_state_variable_name(j));
		}
		printf("\n");
	}
}

void
spins_mce()
{
	int i, j;
	int g = spins_get_label_count();
	for (i = 0; i < g; i++) {
		printf("!%d)\t", i);
		const int *mce = spins_get_label_may_be_coenabled_matrix(i);
		for (j = i+1; j < g; j++) {
			if (!mce[j]) printf("%d, ", j);
		}
		printf("\n");
	}
}

int
main(int argc, char **argv)
{
	printf ("Simulation not implemented for models compiled with -A options.");
	exit(0);
}
