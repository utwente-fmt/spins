
static const size_t 	DB_INIT_SIZE = 4;
static const size_t 	DB_MAX_SIZE = 10;

typedef struct spinja_args_s {
	void			   *model;
	void (*callback)(void* arg, transition_info_t *transition_info, state_t *out);
	void 			   *arg;
	size_t 				outs;
	state_db_t 		   *seen;
	int 				pid;
	int 				real_group;
} spinja_args_t;

extern void dfs (spinja_args_t *args, transition_info_t *transition_info, state_t *state);

void
dfs_cb(void* arg, transition_info_t *transition_info, state_t *out)
{
	spinja_args_t *args = (spinja_args_t *)arg;
	if (leaves_atomic[transition_info->group]) {
	    transition_info->group = args->real_group;
		args->callback (args->arg, transition_info, out);
		args->outs++;
	} else {
		dfs (args, transition_info, out);
	}
}

void
dfs (spinja_args_t *args, transition_info_t *transition_info, state_t *state)
{
	int result = state_db_lookup(args->seen, (const int*)state);
	switch ( result ) {
	case false: { // new state
		state_t out;
		int count = spinja_get_successor_all_real (args->model, state, dfs_cb, args, &out, &args->pid);
		if (count == 0) {
			/*printf ("Loss of atomicity!\n");	// loss of atomicity
			int a;
			for (a = 0; a < spinja_get_state_size(); a++)
				printf("%d, ", ((int*)&state)[a]);
			printf ("\n");*/
		    transition_info->group = args->real_group;
			args->callback (args->arg, transition_info, state);
			args->outs++;
		}
		break;
	}
	case STATE_DB_FULL: // full database
		printf ("ERROR: model's internal atomic state database is filled (max size = 2^%zu). Increase DB_MAX_SIZE.", DB_MAX_SIZE);
		exit(1);
	case true: break; // seen state
	default: break;
	}
}

inline int
reach (void* model, transition_info_t *transition_info, state_t *in,
	   void (*callback)(void* arg, transition_info_t *transition_info, state_t *out),
	   void *arg, int pid) {
	spinja_args_t args;
	args.model = model;
	args.callback = callback;
	args.arg = arg;
	args.outs = 0;
	args.pid = pid;
	args.real_group = transition_info->group;
	args.seen = state_db_create (spinja_get_state_size(), DB_INIT_SIZE, DB_MAX_SIZE);
	dfs (&args, transition_info, in);
	state_db_free (args.seen);
	return args.outs;
}

static int to_get;
static int choice;

void
sim_cb(void* arg, transition_info_t *ti, state_t *out)
{
	state_t *state = (state_t *)arg;
	if (-1 == to_get) {
		printf("\tchoice %d: %s\n", ++choice, spinja_get_group_name(ti->group));
	} else {
		if (++choice == to_get) {
			memcpy(state, out, sizeof(state_t));
		}
	}
}

void
print_state(state_t *state)
{
	int *s = (int *)state;
	int i;
	for (i = 0; i < spinja_get_state_size(); i++) {
		printf("%-30s", spinja_get_state_variable_name(i));
		printf("= ");
		int type = spinja_get_state_variable_type(i);
		int c = spinja_get_type_value_count(type);
		if (0 == c) {
			printf("%3d\n", s[i]);
		} else {
			printf("%s\n", spinja_get_type_value_name(type, s[i]));
		}
	}
}

int
main(int argc, char **argv)
{
	state_t state;
	spinja_get_initial_state(&state);
	while (true) {
		printf("Select a statement\n");
		to_get = -1;
		choice = 0;
		int count = spinja_get_successor_all (NULL, &state, sim_cb, NULL);
		if (0 == count) {
			printf("no executable choices\n\n");
    		print_state(&state);
			exit(0);
		} else {
	        do {
	        	printf("Select [1-%d]: ", choice);
	        	if (scanf("%d", &to_get) != 1) exit(-1);
	        	if (0 == to_get)
	        		print_state(&state);
	        } while (to_get < 1 || to_get > choice);
	        printf("%d\n", to_get);
			choice = 0;
			int count = spinja_get_successor_all (NULL, &state, sim_cb, &state);
		}
	}
}
