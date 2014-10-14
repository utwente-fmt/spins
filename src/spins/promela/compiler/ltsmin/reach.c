
static const size_t 	DB_INIT_SIZE = 4;
static const size_t 	DB_MAX_SIZE = 15;

void
spins_free_args (void *a)
{
    if (a != NULL) {
        spins_state_db_free ((spins_state_db_t *) a);
    }
}

static pthread_once_t spins_key_once = PTHREAD_ONCE_INIT;
static pthread_key_t spins_local_key;

void
spins_initialize_key() {
    pthread_key_create (&spins_local_key, spins_free_args);
}

void
spins_destroy_key() {
    pthread_key_delete (spins_local_key);
}

void
spins_get_table_from_tls (spins_args_t *args)
{
    if (EXPECT_FALSE(args->table == NULL)) {

        (void) pthread_once (&spins_key_once, spins_initialize_key); // barrier
        args->table = pthread_getspecific (spins_local_key);

        if (EXPECT_FALSE(args->table == NULL)) {
            args->table = spins_state_db_create (spins_get_state_size(),
                                                 DB_INIT_SIZE, DB_MAX_SIZE);
            pthread_setspecific (spins_local_key, args->table);
        } else {
            spins_state_db_clear (args->table);
        }
    }
}

extern void spins_dfs (spins_args_t *args, state_t *state, int atomic);

void
spins_atomic_cb (void* arg, transition_info_t *transition_info, state_t *out, int atomic, int *cpy)
{
	spins_args_t *args = (spins_args_t *)arg;
	if (leaves_atomic[transition_info->group]) {
		args->callback (args->arg, args->ti_orig, out, cpy);
		args->outs++;
	} else {
		spins_dfs (args, out, atomic);
	}
}
void
spins_dfs (spins_args_t *args, state_t *state, int atomic)
{
    spins_get_table_from_tls (args);
	int result = spins_state_db_lookup (args->table, (const int*)state);
	switch ( result ) {
	case false: { // new state
		state_t out;
		int count = spins_get_successor_sid (args->model, state, args, &out, atomic);
		if (count == 0) {
			args->callback (args->arg, args->ti_orig, state, args->cpy_orig);
			args->outs++;
		}
		break;
	}
	case STATE_DB_FULL: // full database
		printf ("ERROR: model's internal atomic state database is filled "
		        "(max size = 2^%zu). Increase DB_MAX_SIZE.", DB_MAX_SIZE);
		exit (1);
	case true: break; // seen state
	default: break;
	}
}

inline int
spins_reach (void* model, transition_info_t *transition_info, state_t *in,
	   void (*callback)(void* arg, transition_info_t *transition_info, state_t *out, int *cpy),
	   void *arg, int sid, int *cpy) {
    spins_args_t args;
    args.table = NULL;
    args.model = model;
    args.callback = callback;
    args.arg = arg;
    args.outs = 0;
    args.sid = sid;
    args.ti_orig = transition_info;
    args.cpy_orig = cpy;
	spins_dfs (&args, in, sid);
	return args.outs;
}

static int spins_to_get;
static int spins_choice;
static int spins_pilot = false;
static int spins_match_tid = false;
static int statement_type = -1;

void
spins_sim_cb(void* arg, transition_info_t *ti, state_t *out, int *cpy)
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
		const int *write = spins_get_transition_may_write_dependencies(i);
        const int *must = spins_get_transition_must_write_dependencies(i);
		const int *read = spins_get_transition_read_dependencies(i);
		for (j = 0; j < n; j++) {
			if (read[j]) printf("R(%s), ", spins_get_state_variable_name(j));
			if (must[j]) printf("w(%s), ", spins_get_state_variable_name(j));
			else if (write[j]) printf("W(%s), ", spins_get_state_variable_name(j));
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
		const int *mce = NULL;//spins_get_label_may_be_coenabled_matrix(i);
		for (j = i+1; j < g; j++) {
			if (!mce[j]) printf("%d, ", j);
		}
		printf("\n");
	}
}

static int
main2(int argc, char **argv)
{
	if (argc > 1) {
		if (0 == strcmp(argv[1], "--dm")) {
			spins_dm();
			return 0;
		}
		if (0 == strcmp(argv[1], "--mce")) {
			spins_mce();
			return 0;
		}
		printf("Use %s without arguments to simulate the model behavior. Or use --dm.\n", argv[0]);
		return 0;
	}
	int trans = 0;
	printf("Enter one of the following numbers:\n");
	printf("\t[0-X] to execute a transition.\n");
	printf("\t-1 to print the state.\n");
	printf("\t-2 to change input to group number instead of choice number and back.\n");
	printf("\t-3 to turn on/off the auto pilot (it detects loops).\n");
	printf("\n");
	spins_state_db_t *seen = spins_state_db_create(spins_get_state_size(), DB_INIT_SIZE, DB_MAX_SIZE);
	state_t state;
	spins_get_initial_state(&state);
	int k = spins_get_transition_groups();
	for (statement_type = 0; statement_type < spins_get_type_count(); statement_type++)
	    if (0 == strcmp(spins_get_type_name(statement_type),"statement")) break;
	while (true) {
		int result = spins_state_db_lookup(seen, (const int*)&state);
		if (STATE_DB_FULL == result) {
			printf ("ERROR: state database is filled (max size = 2^%zu). Increase DB_MAX_SIZE.", DB_MAX_SIZE);
			exit(-10);
		}
		printf("Select a statement(%d)\n", trans++);
		spins_to_get = -1;
		spins_choice = 0;
		int count = spins_get_successor_all(NULL, &state, spins_sim_cb, NULL);
		if (0 == count) {
			printf("no executable choices\n\n");
    		spins_print_state(&state);
			exit(0);
		} if (1 == count && spins_pilot && false == result) {
        	printf ("Select [%d-%d]: 1\n", spins_match_tid ? 0 : 1,
        									spins_match_tid ? k : spins_choice);
			int match_tid_old = spins_match_tid;
			spins_match_tid = false;
			spins_to_get = 1;
			spins_choice = 0;
			spins_get_successor_all(NULL, &state, spins_sim_cb, &state);
			//print_state(&state);
			spins_match_tid = match_tid_old;
		} else {
        	do {
	        	printf("Select [%d-%d]: ", spins_match_tid ? 0 : 1,
	        								spins_match_tid ? k : spins_choice);
	        	if (scanf("%d", &spins_to_get) != 1) exit(-1);
	        	if (-1 == spins_to_get)
	        		spins_print_state(&state);
	        	if (-2 == spins_to_get) {
	        		spins_match_tid = !spins_match_tid;
	        		printf ("Turned %s matching of transition ids.\n", spins_match_tid?"on":"off");
	        	}
	        	if (-3 == spins_to_get) {
	        		spins_pilot = !spins_pilot;
	        		printf ("Turned %s autopilot.\n", spins_pilot?"on":"off");
	        	}
	        } while (spins_to_get < (spins_match_tid?0:1) ||(spins_match_tid ? spins_to_get > k : spins_to_get > spins_choice));
			spins_choice = 0;
			spins_get_successor_all(NULL, &state, spins_sim_cb, &state);
		}
	}
}
